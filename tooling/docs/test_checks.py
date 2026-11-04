import json
import os
import subprocess
from pathlib import Path
import shutil
import tempfile
import unittest

from check_links import page_file
from check_translations import check_sources, check_site, source_hash

REPO = Path(__file__).resolve().parents[2]


def write_page(path, fields, body):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text('---\n' + ''.join(f'{key}: {value}\n' for key, value in fields.items()) + '---\n' + body)


class TranslationChecksTest(unittest.TestCase):
    def setUp(self):
        temp = tempfile.TemporaryDirectory()
        self.addCleanup(temp.cleanup)
        self.root = Path(temp.name)
        self.docs = self.root / 'docs'
        shutil.copytree(REPO / 'docs/_data', self.docs / '_data')
        self.en = {'lang': 'en', 'title': 'Setup', 'translation_key': 'setup', 'permalink': '/setup'}
        write_page(self.docs / 'setup.md', self.en, '# Setup\n{: #setup }\n')
        self.de = dict(self.en, lang='de', title='Einrichtung', permalink='/de/setup',
                       source_hash=source_hash(self.docs / 'setup.md'))
        write_page(self.docs / 'de/setup.md', self.de, '# Einrichtung\n{: #setup }\n')
        self.status = self.docs / '_data/translation_status.json'

    def test_valid_translation(self):
        self.assertEqual(check_sources(self.docs, self.status)[0], [])
        self.assertEqual(json.loads(self.status.read_text()), {})

    def test_changed_source_marks_stale_without_failing(self):
        with (self.docs / 'setup.md').open('a') as source:
            source.write('\nNew information\n')
        self.assertEqual(check_sources(self.docs, self.status)[0], [])
        self.assertEqual(json.loads(self.status.read_text()), {'de/setup.md': {'stale': True}})

    def test_missing_translation_is_allowed(self):
        (self.docs / 'de/setup.md').unlink()
        errors, summary = check_sources(self.docs, self.status)
        self.assertEqual(errors, [])
        languages = json.loads((self.docs / '_data/languages.json').read_text())
        self.assertIn(f'{len(languages)} untranslated', summary)

    def test_wrong_route_and_missing_hash_are_rejected(self):
        self.de['permalink'] = '/setup'
        del self.de['source_hash']
        write_page(self.docs / 'de/setup.md', self.de, '# Einrichtung\n{: #setup }\n')
        errors, _ = check_sources(self.docs, self.status)
        self.assertTrue(any('permalink should be /de/setup' in e for e in errors))
        self.assertTrue(any('source_hash missing' in e for e in errors))

    def test_explicit_heading_ids_are_required(self):
        write_page(self.docs / 'de/setup.md', self.de, '# Einrichtung\n')
        self.assertTrue(any('heading without' in e for e in check_sources(self.docs, None)[0]))

    def test_ui_keys_are_required(self):
        ui = self.docs / '_data/ui/de.json'
        data = json.loads(ui.read_text())
        del data['skip']
        ui.write_text(json.dumps(data))
        self.assertTrue(any('skip must be' in e for e in check_sources(self.docs, None)[0]))

    def test_html_homepage_is_checked(self):
        en = dict(self.en, translation_key='index', permalink='/')
        write_page(self.docs / 'index.html', en, '<h1 id="home">Home</h1>')
        de = dict(en, lang='de', permalink='/de/', source_hash=source_hash(self.docs / 'index.html'))
        write_page(self.docs / 'de/index.html', de, '<h1 id="home">Start</h1>')
        self.assertEqual(check_sources(self.docs, None)[0], [])
        with (self.docs / 'index.html').open('a') as source:
            source.write('<p>Changed</p>')
        check_sources(self.docs, self.status)
        self.assertIn('de/index.html', json.loads(self.status.read_text()))

    def test_root_legal_pages_and_vendor_are_not_translations(self):
        write_page(self.docs / 'PRIVACY_POLICY.md', {'permalink': '/privacy', 'layout': 'plain'}, '# Privacy')
        vendor = self.docs / 'vendor/bundle'
        vendor.mkdir(parents=True)
        (vendor / 'README.md').write_text('Gem documentation')
        self.assertEqual(check_sources(self.docs, None)[0], [])

    def test_rendered_headings_must_match_and_links_use_translations(self):
        site = self.root / 'site'
        (site / 'de').mkdir(parents=True)
        (site / 'setup.html').write_text('<main><h1 id="setup">Setup</h1></main>')
        (site / 'de/setup.html').write_text('<main><h1 id="wrong">Einrichtung</h1><a href="/setup">Setup</a></main>')
        errors, _ = check_site(self.docs, site)
        self.assertTrue(any('headings differ' in e for e in errors))
        self.assertTrue(any('links English' in e for e in errors))
        (site / 'de/setup.html').write_text('<main><h1 id="setup">Einrichtung</h1><a href="/de/setup">Setup</a></main>')
        self.assertEqual(check_site(self.docs, site)[0], [])


class LinkChecksTest(unittest.TestCase):
    def test_routes_match_pages_serving(self):
        with tempfile.TemporaryDirectory() as temp:
            site = Path(temp)
            (site / 'de').mkdir()
            (site / 'de/index.html').touch()
            (site / 'setup.html').touch()
            self.assertEqual(page_file(site, '/de/'), site / 'de/index.html')
            self.assertEqual(page_file(site, '/setup'), site / 'setup.html')
            self.assertIsNone(page_file(site, '/setup/'))
            self.assertIsNone(page_file(site, '/de/missing'))


class RenderedShellTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        temp = tempfile.TemporaryDirectory()
        cls.addClassCleanup(temp.cleanup)
        root = Path(temp.name)
        source = root / 'source'
        cls.site = root / 'site'
        for directory in ('_layouts', '_includes', '_data'):
            shutil.copytree(REPO / 'docs' / directory, source / directory)
        (source / '_data/translation_status.json').write_text(json.dumps({'de/setup.md': {'stale': True}}))
        (source / '_config.yml').write_text('''title: airplanes.live App
url: https://apl.darken.eu
repository_url: https://github.com/d4rken-org/airplanes-live-app
defaults:
  - scope: {path: ""}
    values: {layout: default, lang: en}
''')
        for lang in ('en', 'de'):
            for key in ('index', 'getting-started', 'map', 'search', 'watchlist', 'feeders', 'setup', 'faq', 'bugs'):
                if lang == 'de' and key == 'bugs':
                    continue
                prefix = '' if lang == 'en' else '/de'
                fields = {'title': key, 'lang': lang, 'translation_key': key,
                          'permalink': prefix + ('/' if key == 'index' else '/' + key)}
                if key == 'index':
                    fields['home'] = 'true'
                write_page(source / ('' if lang == 'en' else lang) / (key + '.md'), fields, '# Page\n')
        write_page(source / 'privacy.md', {'title': 'Privacy', 'layout': 'plain', 'permalink': '/privacy'}, '# Privacy\n')
        write_page(source / 'changelog.md', {'title': 'Changelog', 'translation_key': 'changelog',
                                           'permalink': '/changelog', 'layout': 'plain'}, '# Changelog\n')
        result = subprocess.run(['bundle', 'exec', 'jekyll', 'build', '--source', str(source),
                                 '--destination', str(cls.site), '--strict_front_matter'],
                                cwd=source, env=dict(os.environ, BUNDLE_GEMFILE=str(REPO / 'docs/Gemfile')),
                                text=True, capture_output=True)
        if result.returncode:
            raise AssertionError(result.stdout + result.stderr)

    def test_equivalent_page_switches_and_alternates(self):
        page = (self.site / 'de/setup.html').read_text()
        self.assertIn('<html lang="de" dir="ltr">', page)
        self.assertIn('hreflang="en" href="https://apl.darken.eu/setup"', page)
        self.assertIn('hreflang="de" href="https://apl.darken.eu/de/setup"', page)
        self.assertIn('<details class="language-menu">', page)
        self.assertIn('<summary aria-label="Sprache: Deutsch">', page)
        self.assertIn('<span class="language-name">Deutsch</span>', page)
        self.assertIn('href="/security" hreflang="en">Downloads überprüfen', page)
        self.assertIn('href="/bugs" hreflang="en">Hilfe erhalten', page)
        self.assertIn('class="github" href="https://github.com/d4rken-org/airplanes-live-app" aria-label="GitHub"', page)
        self.assertIn('data-language="de"', page)
        self.assertNotIn('<details class="language-menu">', (self.site / 'bugs.html').read_text())

    def test_missing_translation_falls_back_without_language_caveat(self):
        page = (self.site / 'de/setup.html').read_text()
        self.assertIn('href="/bugs" hreflang="en">Probleme melden', page)
        self.assertNotIn('/de/bugs', page)
        self.assertNotIn('(Englisch)', page)
        self.assertIn('href="/changelog" hreflang="en">Änderungsprotokoll', page)

    def test_stale_notice_links_to_english(self):
        page = (self.site / 'de/setup.html').read_text()
        self.assertIn('Diese Übersetzung ist möglicherweise nicht mehr aktuell.', page)
        self.assertIn('href="/setup" hreflang="en" data-language="en"', page)
        self.assertNotIn('Diese Übersetzung ist möglicherweise nicht mehr aktuell.',
                         (self.site / 'de/map.html').read_text())

    def test_language_suggestion_only_on_translatable_english_pages(self):
        self.assertIn('class="notice language-suggestion" role="note" hidden',
                      (self.site / 'setup.html').read_text())
        self.assertNotIn('language-suggestion', (self.site / 'de/setup.html').read_text())
        self.assertNotIn('language-suggestion', (self.site / 'bugs.html').read_text())

    def test_legal_pages_stay_standalone(self):
        page = (self.site / 'privacy.html').read_text()
        self.assertNotIn('class="guide-nav"', page)
        self.assertNotIn('hreflang=', page)
        self.assertIn('class="standalone prose"', page)


if __name__ == '__main__':
    unittest.main()
