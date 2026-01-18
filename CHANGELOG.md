## [1.0.0](https://github.com/TheArqsz/BurpSense/compare/v0.2.3...v1.0.0) (2026-01-18)


### ⚠ BREAKING CHANGES

* **security:** replace hardcoded salt with per-encryption random salt

### Features

* **security:** add rate limiting to the bridge ([9f4b7b1](https://github.com/TheArqsz/BurpSense/commit/9f4b7b1499c51ad0fffb2b662a6971d172275b44))


### Bug Fixes

* **bridge:** improved regex validation ([342ee77](https://github.com/TheArqsz/BurpSense/commit/342ee77b3620b9f7ee6d999550b8c3e481f7caa9))
* **bridge:** remove failed WebSocket channels from active clients ([52d0f17](https://github.com/TheArqsz/BurpSense/commit/52d0f17ca406e6eca146edbc9883ee282e01b0d2))
* **security:** replace hardcoded salt with per-encryption random salt ([071431f](https://github.com/TheArqsz/BurpSense/commit/071431f43b34fb18c78676e9487f686ca35f9e11))
* **vscode:** improve line number validation for large files ([8767cde](https://github.com/TheArqsz/BurpSense/commit/8767cde841f0ba0c9f11307fadf7f39204e89cf5))
* **vscode:** prevent memory leak from uncleared cache cleanup interval ([66fdc50](https://github.com/TheArqsz/BurpSense/commit/66fdc507548a40fc1d51b5ac632112f2e542d009))
* **vscode:** prevent race conditions in connection status updates ([2c44cdb](https://github.com/TheArqsz/BurpSense/commit/2c44cdb661621d6a66680994dd3b79f7efc368b4))


### Performance Improvements

* **vscode:** eliminate duplicate full scan in drift detection ([1a099ba](https://github.com/TheArqsz/BurpSense/commit/1a099ba82b4d2a4bc3415a2a8148ab613b4874a2))
* **vscode:** optimize similarity detection with caching and early exits ([10cf916](https://github.com/TheArqsz/BurpSense/commit/10cf91694e1e29de63b34c190421920e858af37b))

## [0.2.3](https://github.com/TheArqsz/BurpSense/compare/v0.2.2...v0.2.3) (2026-01-18)


### Bug Fixes

* **deps:** replace node-fetch with native fetch ([4eeb720](https://github.com/TheArqsz/BurpSense/commit/4eeb72055be9007dda804be5598262307b31c646))
* **lifecycle:** implement deactivate cleanup ([ae12058](https://github.com/TheArqsz/BurpSense/commit/ae12058819f5e2b208f076fd7c5a4557ae9e1523))
* **workflow:** fixed path in publish part of the workflow ([f407206](https://github.com/TheArqsz/BurpSense/commit/f407206e1a002fee52954bd38ea5b6850d56885a))

## [0.2.2](https://github.com/TheArqsz/BurpSense/compare/v0.2.1...v0.2.2) (2026-01-18)


### Bug Fixes

* **mapping:** fixed error handling and logging on first launch ([22ac8c4](https://github.com/TheArqsz/BurpSense/commit/22ac8c4da3b63629055185cc2abdbfcd44ccb69c))

## [0.2.1](https://github.com/TheArqsz/BurpSense/compare/v0.2.0...v0.2.1) (2026-01-18)


### Bug Fixes

* **reconnection:** increased reconnection base time to avoid frequent notification spam ([b699b80](https://github.com/TheArqsz/BurpSense/commit/b699b800621c5f09e46cd693012543d1e0e1b9dc))

## [0.2.0](https://github.com/TheArqsz/BurpSense/compare/v0.1.0...v0.2.0) (2026-01-18)


### Features

* **vscode:** add 'Stay Offline' option to connection error dialogs ([ad2ed9f](https://github.com/TheArqsz/BurpSense/commit/ad2ed9f81e6cac69dd2079863a4b23747607f163))

## [0.1.0](https://github.com/TheArqsz/BurpSense/compare/57b64b8f1b73d10daa52eb33052709df6c750448...v0.1.0) (2026-01-14)


### Features

* Initial commit with working copy of the project ([57b64b8](https://github.com/TheArqsz/BurpSense/commit/57b64b8f1b73d10daa52eb33052709df6c750448))

