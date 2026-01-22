## [1.1.0](https://github.com/TheArqsz/BurpSense/compare/v1.0.4...v1.1.0) (2026-01-22)


### Features

* **connection:** added auto connect at startup setting ([ce3e15e](https://github.com/TheArqsz/BurpSense/commit/ce3e15ebe7282a4c15bac5f40016229ca33fde9a))


### Bug Fixes

* **activation:** remove automatic connection attempt on startup ([889e6ae](https://github.com/TheArqsz/BurpSense/commit/889e6aec5e5cc293e812fda36de1907de34b6180))

## [1.0.4](https://github.com/TheArqsz/BurpSense/compare/v1.0.3...v1.0.4) (2026-01-20)


### Bug Fixes

* **advisoryPanel:** switched from dompurify to sanitize-html ([c6a11a8](https://github.com/TheArqsz/BurpSense/commit/c6a11a8c77e36523f6e05639eefc29b3748884cb))
* **mapping:** harden path normalization against traversal escapes ([5f65b10](https://github.com/TheArqsz/BurpSense/commit/5f65b106d5704a4c8957dcfe477d067f62c5d232))
* **ui:** ensure bridge error dialogs run on EDT ([0f4dbe1](https://github.com/TheArqsz/BurpSense/commit/0f4dbe152cba6130a74bd2e5409c563e7dd04344))
* **ui:** use hex nonces to resolve CSP ([c40ac03](https://github.com/TheArqsz/BurpSense/commit/c40ac03db15fff31ac09054ce7f3fbd0860fbd6e))
* **workflow:** updated release workflow ([d4df793](https://github.com/TheArqsz/BurpSense/commit/d4df7932f7fc3d56f433fbbd85e84faf84f8f7ef))
* **workflow:** updated release workflow ([af43ba0](https://github.com/TheArqsz/BurpSense/commit/af43ba03a2e738eaa468b38c34c64204dc7195d4))

## [1.0.3](https://github.com/TheArqsz/BurpSense/compare/v1.0.2...v1.0.3) (2026-01-19)


### Bug Fixes

* **security:** sanitize HTML content in issue advisory panel ([9e547b9](https://github.com/TheArqsz/BurpSense/commit/9e547b9b8f0561b1c5e991d54f772ba1c69efb41))
* **security:** use constant-time comparison for API token validation ([4e6da56](https://github.com/TheArqsz/BurpSense/commit/4e6da56ce1d2ad6e7ce39b593930fe3ecca0a4bd))

## [1.0.2](https://github.com/TheArqsz/BurpSense/compare/v1.0.1...v1.0.2) (2026-01-19)


### Bug Fixes

* **connection:** downgrade expected errors from ERROR to WARN level ([4eb56a3](https://github.com/TheArqsz/BurpSense/commit/4eb56a3a6e9bb63a565e48f97bd670ddbba78223))
* **logger:** sanitize error stack traces and add formatting ([84aab2c](https://github.com/TheArqsz/BurpSense/commit/84aab2cc431292d52bbe28eeb5fed562e300c84a))
* **ui:** sanitize error messages shown to users ([aee6f11](https://github.com/TheArqsz/BurpSense/commit/aee6f11d9f5a49aa0ec076cb794d324584b7b5bb))

## [1.0.1](https://github.com/TheArqsz/BurpSense/compare/v1.0.0...v1.0.1) (2026-01-19)


### Bug Fixes

* **bridge:** fixed wrong middleware handling order ([4ecd289](https://github.com/TheArqsz/BurpSense/commit/4ecd28991cfd93a9fdb4199670b11ec04e72af9a))
* **bridge:** improved websocket cleanup ([1d7b4f3](https://github.com/TheArqsz/BurpSense/commit/1d7b4f33d6f7899011a1f6d3c65c883d2a5551f7))
* **bridge:** prevent NPE in SingleIssueHandler when path parameters map is null ([3440fb7](https://github.com/TheArqsz/BurpSense/commit/3440fb705dc4548f3ddba76615276a90508ffb35))
* **constants:** fix datetime precision ([381726f](https://github.com/TheArqsz/BurpSense/commit/381726ff8e74dce82dbb87d385d2db645987b554))
* **rateLimiter:** fixed units (getEpochSecond -> currentTmeMillis) ([b996470](https://github.com/TheArqsz/BurpSense/commit/b996470a998982c5496070d0ca9793b736ebb239))
* **regexValidator:** added single ExecutorService to lower number of created threads per verification ([3761971](https://github.com/TheArqsz/BurpSense/commit/37619713eaf5cd876769dd7a94692a71810b1551))
* update release job conditions in release.yml ([4b4b0b4](https://github.com/TheArqsz/BurpSense/commit/4b4b0b4beeb863e6dae95b351ba4ed43116ca061))
* **workflow:** added npm ci to extension workflow ([ae81214](https://github.com/TheArqsz/BurpSense/commit/ae812145596a02b21a698d0be4420a44dca0b189))
* **workflow:** added working-directory param to workflows ([880e7b2](https://github.com/TheArqsz/BurpSense/commit/880e7b2c941b7ca9268c45a3db61e0209e284c69))


### Reverts

* **IssueJsonMapper:** reverted addIfNotNull change ([0b2847e](https://github.com/TheArqsz/BurpSense/commit/0b2847eee9e6ecb64032a58f5a9b92f740fee57a))

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

