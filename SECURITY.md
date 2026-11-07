---
layout: plain
permalink: /security
title: "Security"
---

# Security

## Verifying APK authenticity

You can verify a downloaded or installed APK against the fingerprints below using
[`apksigner`](https://developer.android.com/tools/apksigner) or
[AppVerifier](https://github.com/soupslurpr/AppVerifier):

```bash
apksigner verify --print-certs path/to/app.apk
```

### GitHub releases and IzzyOnDroid

IzzyOnDroid ships the same APKs as the GitHub releases, signed with this certificate:

```
SHA-256: 63:86:6B:8A:A6:9D:26:93:83:2A:2F:49:71:90:23:13:09:79:FE:30:1A:7F:53:11:F4:A1:29:CC:76:30:8A:9A
SHA-1:   FD:EA:07:2B:C3:6F:CB:D8:5C:69:DB:F9:7C:2B:F8:FA:BC:1A:A8:55
MD5:     F2:76:64:77:7D:B7:B8:50:6C:59:0D:EE:A6:83:54:CB
```

`apksigner` prints the SHA-256 digest in lowercase without colons:

```
63866b8aa69d2693832a2f49719023130979fe301a7f5311f4a129cc76308a9a
```

If the fingerprint doesn't match, don't install the APK and
[open an issue](https://github.com/d4rken-org/airplanes-live-app/issues).

## Reproducible builds

The release APKs are checked for reproducibility, so anyone can confirm that a release was built
from the published source. See the
[current status](https://shields.rbtlog.dev/eu.darken.apl).
