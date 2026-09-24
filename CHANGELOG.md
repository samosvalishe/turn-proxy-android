# Changelog

## [5.0.2](https://github.com/samosvalishe/turn-proxy-android/compare/v5.0.1...v5.0.2) (2026-09-24)


### Fixes

* bump ([9e3e753](https://github.com/samosvalishe/turn-proxy-android/commit/9e3e753bbbd8eea0bfa60496827b17579058f3b0))

## [5.0.1](https://github.com/samosvalishe/turn-proxy-android/compare/v5.0.0...v5.0.1) (2026-09-23)


### Fixes

* proguard-rules.pro ([f3acd78](https://github.com/samosvalishe/turn-proxy-android/commit/f3acd7812b40148e3f03b813cbf3e731806f269d))

## [5.0.0](https://github.com/samosvalishe/turn-proxy-android/compare/v4.2.0...v5.0.0) (2026-09-23)


### ⚠ BREAKING CHANGES

* bump aar

### Features

* bump aar ([cfb5d27](https://github.com/samosvalishe/turn-proxy-android/commit/cfb5d273db1f06f5c9f0151fcbc19153850396d8))
* bump aar ([082251b](https://github.com/samosvalishe/turn-proxy-android/commit/082251bd63154d2d213b40358457096ff3773e84))
* **diagnostics:** отмечать процесс и сессию в событиях сервиса ([82d3fb8](https://github.com/samosvalishe/turn-proxy-android/commit/82d3fb8c734814f8c5080f962711c11ec40e967c))
* **i18n:** английская локаль и выбор языка в настройках ([e747be0](https://github.com/samosvalishe/turn-proxy-android/commit/e747be0807c464553ac58726c6e2080b5e48072b))
* **notify:** провайдер в уведомлении, без счётчика потоков в direct ([1d73a97](https://github.com/samosvalishe/turn-proxy-android/commit/1d73a976721d113aca1a9e9688f732435f87bcd8))
* **proxy:** освобождать аллокации до потери сети ([d9af18a](https://github.com/samosvalishe/turn-proxy-android/commit/d9af18ab7a282915ae1e1d3c31b14cd4c71bbb75))
* **proxy:** переподключение вместо перезапуска ядра при смене сети ([e070d12](https://github.com/samosvalishe/turn-proxy-android/commit/e070d121e3351b82d3ceed6893f7bac2f733e856))
* **proxy:** провайдер Relay/Direct в листе серверов, без упоминаний зла ([0d0be57](https://github.com/samosvalishe/turn-proxy-android/commit/0d0be5704474182729d969a7527f846c4ed3fca2))
* **proxy:** режим проброса tcp с настраиваемым ARQ ([e7425a5](https://github.com/samosvalishe/turn-proxy-android/commit/e7425a5ab8df6731c2761684647ce1e26736f235))
* rebranding ([5d3db4d](https://github.com/samosvalishe/turn-proxy-android/commit/5d3db4d3a43f32c348cc61bea992dc9ed6e45584))
* **server:** install.sh из релиза ядра, apply вместо install/start (RPC proto 3) ([4304162](https://github.com/samosvalishe/turn-proxy-android/commit/430416251ce58a57f803a0db0393fa554a7ba320))
* **server:** настройка пейсинга обфускации ([d16772d](https://github.com/samosvalishe/turn-proxy-android/commit/d16772da88e19df670974c3ee889c20871eccf9f))
* **service:** история завершений процесса в логе, ошибки старта в ресурсах ([a9489c4](https://github.com/samosvalishe/turn-proxy-android/commit/a9489c40d206a91652ad0c14acb712f2a2889577))
* **settings:** тумблер сезонного декора ([bb0a40b](https://github.com/samosvalishe/turn-proxy-android/commit/bb0a40ba9b678fe5679a7bb0f4f3311cae28a4a3))
* **share:** доступы из client-list вместо WG-пиров ([0350afa](https://github.com/samosvalishe/turn-proxy-android/commit/0350afaa411bcdacdcd1becc8482830ad104fd21))
* **share:** опциональная ссылка на звонок в freeturn:// ([0f8dc7c](https://github.com/samosvalishe/turn-proxy-android/commit/0f8dc7cf1e521debd63aed54617693c386822654))
* **tcp:** вернуть настройку bond ([6e8cbc9](https://github.com/samosvalishe/turn-proxy-android/commit/6e8cbc952f76bf8d809f137774157598717dcd62))
* **ui:** M3 Expressive - connected button group, меню с выбором, морф кнопок и иконок ([deb37d6](https://github.com/samosvalishe/turn-proxy-android/commit/deb37d6c2b20d05dbe9042798e3f28d97f1085ac))
* **ui:** осенний декор кнопки-героя ([f430dea](https://github.com/samosvalishe/turn-proxy-android/commit/f430dea774889fc4a4e0c6a92c79c623760c3c07))
* **ui:** тактильный тик при вращении листа ([bba32ea](https://github.com/samosvalishe/turn-proxy-android/commit/bba32ea0511b915a4b2ceb719a557bf95a1df44e))


### Fixes

* autoConnect в бэкапе, destroy webview капчи, иконка уведомления ([df4c5d9](https://github.com/samosvalishe/turn-proxy-android/commit/df4c5d9af14beff442c9cc83c23ab8d75da91399))
* **data:** дефолты потоков при разборе серверов из ClientConfig ([30d1dc1](https://github.com/samosvalishe/turn-proxy-android/commit/30d1dc1be0d0e4c1f3a96e111fe42910cf1463e6))
* **data:** не затирать серверы битым JSON и не глушить битую обфускацию ([0f75931](https://github.com/samosvalishe/turn-proxy-android/commit/0f75931ca67b402db76e9aecfcf198a7cff562e6))
* **data:** не показывать успех, когда сервер не сохранился ([66513a2](https://github.com/samosvalishe/turn-proxy-android/commit/66513a243b95f7eb64659d701a33e0bc33bad3ff))
* **i18n:** метки выбора бэкенда различают наш ft-wg0 и VPN пользователя ([ead15bc](https://github.com/samosvalishe/turn-proxy-android/commit/ead15bceda2942de1b725d93ff413a8b83eef0be))
* **log:** ограниченная очередь лога, экспорт через неё же ([5db1bf4](https://github.com/samosvalishe/turn-proxy-android/commit/5db1bf47127ffadb5ab5cda30335ec7d14cdef4d))
* **network:** смена сети при том же интерфейсе, dns без рецикла ([87e90db](https://github.com/samosvalishe/turn-proxy-android/commit/87e90db2dff5fe4beddcf5047c2932d37ff1b4ac))
* **privacy:** маскировать -links в логе и не бэкапить логи сессии ([8691c76](https://github.com/samosvalishe/turn-proxy-android/commit/8691c7685bec0b62ed661b9f551dba72160ff2b6))
* **proxy:** гасить ошибку ядра по таймеру и выключить его буфер логов ([00af5a9](https://github.com/samosvalishe/turn-proxy-android/commit/00af5a902a4e7c2376d790df7fada3d110e9b182))
* **proxy:** сохранять ручной DNS при смене сети и чинить SOCKS5-раздачу ([88dc0d1](https://github.com/samosvalishe/turn-proxy-android/commit/88dc0d10380fc1628ed90a35aacd2beb2be1d709))
* **proxy:** учитывать актуальность сессии при запуске и восстановлении ([53941bd](https://github.com/samosvalishe/turn-proxy-android/commit/53941bdf6dc4b684febef8ef1e2aced1d6184a1e))
* **security:** закрыть трамплин прокси и ограничить итерации pbkdf2 ([8d07f6d](https://github.com/samosvalishe/turn-proxy-android/commit/8d07f6d1ab8f6f5b0fb22f2f9d0693fdaec3a73e))
* **service:** запрос VPN-разрешения при старте из виджета и тайла ([29dc9a1](https://github.com/samosvalishe/turn-proxy-android/commit/29dc9a199979f8cb5a3206288dd24fe744574daa))
* **service:** команды запуска одной очередью, рестарт не перетирает стоп ([428c84e](https://github.com/samosvalishe/turn-proxy-android/commit/428c84e9ad2e294e3e4e0eb35f097bea8619efbe))
* **service:** конфиг старта одним снимком профиля ([2305ae0](https://github.com/samosvalishe/turn-proxy-android/commit/2305ae01277eca8996390aa4ee6506d9cc235578))
* **service:** события ядра и сети адресованы своей сессии ([28506b0](https://github.com/samosvalishe/turn-proxy-android/commit/28506b0263ab14e375cbd7c6858dcb5a302f8c20))
* **settings:** не терять правки и рестарт пары при уходе с экрана ([a6caf4e](https://github.com/samosvalishe/turn-proxy-android/commit/a6caf4e8f45ae729ce9442c58bee381763d92293))
* **settings:** убрать моргание статуса обновления ([92c1afc](https://github.com/samosvalishe/turn-proxy-android/commit/92c1afcfb0489851ef925ae99e679de90b0bca19))
* **share:** импорт полей ядра и пейсинг обфускации в ссылке ([0d88536](https://github.com/samosvalishe/turn-proxy-android/commit/0d88536933b6cc13829021be0d0f51cc5a1419ef))
* **socks5:** предел клиентов, сброс при отказе protect ([4a7952b](https://github.com/samosvalishe/turn-proxy-android/commit/4a7952b7ef95172b2b6f38fa261f3ba1ab95d9cf))
* **tunnel:** сохранять параметры amneziawg и показывать протокол доступа ([315f07f](https://github.com/samosvalishe/turn-proxy-android/commit/315f07ff534cd357b4f54b6daefcef1673be1d4d))
* **ui:** отступы пунктов в дропдаунах, короткие режимы split-tunnel ([49784b6](https://github.com/samosvalishe/turn-proxy-android/commit/49784b61f97ac4ba88614b90f68fc8ce32a6d0ef))
* **vpn:** не отзывать чужой VPN при открытии приложения ([a380d2c](https://github.com/samosvalishe/turn-proxy-android/commit/a380d2cfa4c1a170a232b24695925364683cac39))
* **vpn:** пустой include-список больше не заворачивает весь трафик ([9627f2a](https://github.com/samosvalishe/turn-proxy-android/commit/9627f2a3c9b2cb153bd370d7ab2966f7f8a832c7))


### Performance

* **prefs:** разбор серверов только при их изменении ([017876d](https://github.com/samosvalishe/turn-proxy-android/commit/017876d62eba93ae41f3fc129d8db94660694e12))
* **proxy:** снять старт сервиса с главного потока ([392e8be](https://github.com/samosvalishe/turn-proxy-android/commit/392e8bed69a8d3dae4791c69d5cb77a2e026c485))
* **proxy:** убрать опрос метрик ядра ([f68b526](https://github.com/samosvalishe/turn-proxy-android/commit/f68b526a26af2955620c23bab73d4daccebd88a1))
* **service:** сравнивать нотификацию по полям вместо списка-снимка ([cc54b28](https://github.com/samosvalishe/turn-proxy-android/commit/cc54b2892fbb355c4e1277db020732935fbf887e))
* **ui:** не рекомпозировать всю кнопку героя на каждом кадре морфа ([03ec6b0](https://github.com/samosvalishe/turn-proxy-android/commit/03ec6b0eb54381bbde3f396d7102fdbcf99628ea))


### Refactoring

* **data:** общий ByteArray.toHex вместо трёх копий форматирования ([80ed5dc](https://github.com/samosvalishe/turn-proxy-android/commit/80ed5dcb1a6e5c6db905add99080d18efd9d3925))
* **proxy:** развалить глобальный ProxyStore на Koin-синглтоны состояния и лога ([13380fc](https://github.com/samosvalishe/turn-proxy-android/commit/13380fca7e99a6a8d0bd31f385f8ec99d73b94d7))
* **service:** один флаг остановки сессии ([47a5650](https://github.com/samosvalishe/turn-proxy-android/commit/47a5650e84bcef9765413a049331cda1c6f947c7))
* **ssh:** типизированный результат вместо строк ERROR ([2cfa906](https://github.com/samosvalishe/turn-proxy-android/commit/2cfa906606233ebf36e5c705328c1bd83dd253df))
* **ui:** отступы через Spacing, доп. цвета сводятся к тону схемы ([f6257cb](https://github.com/samosvalishe/turn-proxy-android/commit/f6257cb14193b3205bd83ab87c1ab7989b0fc318))
* **update:** типизированные ошибки обновления, тексты в ресурсах ([66fb675](https://github.com/samosvalishe/turn-proxy-android/commit/66fb675bd2c49a327a3b9101a0875d5954f80b47))
* **viewmodel:** разнести SettingsViewModel и увести вибрацию за интерфейс ([377e0f7](https://github.com/samosvalishe/turn-proxy-android/commit/377e0f74ce30e90a5093abac3465fe213403c0dc))

## [4.2.0](https://github.com/samosvalishe/turn-proxy-android/compare/v4.1.0...v4.2.0) (2026-08-19)


### Features

* bump aar ([f0aa0bb](https://github.com/samosvalishe/turn-proxy-android/commit/f0aa0bbf27764da94695840b0658c1beb56487be))

## [4.1.0](https://github.com/samosvalishe/turn-proxy-android/compare/v4.0.0...v4.1.0) (2026-08-18)


### Features

* **logs:** файловый лог сессии с ротацией и отправкой ([0f9f9be](https://github.com/samosvalishe/turn-proxy-android/commit/0f9f9be46c58f87eb25f16e6954ee03c6180f0c7))
* **settings:** подключение при запуске и работа в фоне ([c85e3e0](https://github.com/samosvalishe/turn-proxy-android/commit/c85e3e08e82e88d0105eb44004286743c9a9cfb6))


### Fixes

* **proxy:** sticky-рестарт не поднимает сессию без намерения ([cccc711](https://github.com/samosvalishe/turn-proxy-android/commit/cccc711b11e4003da8145e80a395530ad6870e01))
* **proxy:** неудачный tun при смене сети не рвёт сессию ([36b123b](https://github.com/samosvalishe/turn-proxy-android/commit/36b123be46a762eceee2b11ec5aa147e134ab9c6))

## [4.0.0](https://github.com/samosvalishe/turn-proxy-android/compare/v3.6.0...v4.0.0) (2026-08-17)


### ⚠ BREAKING CHANGES

* **proxy:** убрать TCP-режим проброса и bond
* **proxy:** ядро gomobile в процессе приложения

### Features

* **proxy:** убрать TCP-режим проброса и bond ([215156e](https://github.com/samosvalishe/turn-proxy-android/commit/215156ea44d351f6fcde7d86d4b91259f6e06ed0))
* **proxy:** ядро gomobile в процессе приложения ([9c05bd9](https://github.com/samosvalishe/turn-proxy-android/commit/9c05bd9e76afa2276e2b5a6c4dad39d0f525616a))
* **settings:** пункт Always-on VPN в разделе "Работа в фоне" ([429affa](https://github.com/samosvalishe/turn-proxy-android/commit/429affa0a19f1280195857e2e4974b80ce603eed))


### Fixes

* bump aar ([be61dbc](https://github.com/samosvalishe/turn-proxy-android/commit/be61dbc69b66fc324aafc09e38135f088818535b))
* **proxy:** вернуть wake lock сессии, убрать always-on vpn ([0e0d3ee](https://github.com/samosvalishe/turn-proxy-android/commit/0e0d3ee952a0e3428cd928e1b32ed02e585475e3))
* пересоздание TUN-интерфейса при смене сети для обхода обрывов в Doze mode ([9bfd79a](https://github.com/samosvalishe/turn-proxy-android/commit/9bfd79a854de614af5dd73e9a860a8b59742a7a2))


### Performance

* **proxy:** экономия батареи - без wake lock, опрос метрик только при видимом окне ([82c1adf](https://github.com/samosvalishe/turn-proxy-android/commit/82c1adffb6239ca0f189f6904b5222e60b1c0195))

## [3.6.0](https://github.com/samosvalishe/turn-proxy-android/compare/v3.5.2...v3.6.0) (2026-08-14)


### Features

* update core ([31b6fc6](https://github.com/samosvalishe/turn-proxy-android/commit/31b6fc6fe17bdd7e3a542c8bf059ce4f6e62f913))

## [3.5.2](https://github.com/samosvalishe/turn-proxy-android/compare/v3.5.1...v3.5.2) (2026-08-04)


### Fixes

* **logs:** починен автоскролл к хвосту ([d52cceb](https://github.com/samosvalishe/turn-proxy-android/commit/d52cceb90c5a1fdbb1699b9ea9267b6f0a1d1ca5))
* **ui:** в шторке при WG - "Туннель активен" ([0cfaae2](https://github.com/samosvalishe/turn-proxy-android/commit/0cfaae2ac34d6bd91c1ab44357ddfb73d85b4008))
* **ui:** статистика и статус туннеля в WG-режиме ([009baae](https://github.com/samosvalishe/turn-proxy-android/commit/009baaeabc77d2e3b0ab275e71a2b661d3c1d492))

## [3.5.1](https://github.com/samosvalishe/turn-proxy-android/compare/v3.5.0...v3.5.1) (2026-07-30)


### Fixes

* **a11y:** описание QR-кода и роль кнопки у чипа split-tunnel ([2573d47](https://github.com/samosvalishe/turn-proxy-android/commit/2573d47c1c33193dd9fe302763f63e1a779673d9))
* **setup:** поля не затираются дефолтом до приезда DataStore ([50a03f5](https://github.com/samosvalishe/turn-proxy-android/commit/50a03f5ff49717aeeb8d25482c07717cc6e572a2))
* update бинарников ([2775435](https://github.com/samosvalishe/turn-proxy-android/commit/2775435472579a395626f55ba7ac9cf63c8d4392))
* гонки в колбэке камеры и в отложенном reconnect ([2607bd7](https://github.com/samosvalishe/turn-proxy-android/commit/2607bd7dc2e6bb91b5cc5ea67b4f5a912c5ecd83))


### Performance

* **ui:** меньше аллокаций и рекомпозиций на кадр ([f99e18c](https://github.com/samosvalishe/turn-proxy-android/commit/f99e18c024c91712a78edcdb8bff3d0d257299d4))


### Refactoring

* **logs:** уровень строки считается один раз в domain ([e78c335](https://github.com/samosvalishe/turn-proxy-android/commit/e78c335e2fee783780f046789339e4a688cb5e18))

## [3.5.0](https://github.com/samosvalishe/turn-proxy-android/compare/v3.4.2...v3.5.0) (2026-07-29)


### Features

* **client:** убран выбор браузерного профиля ([85e7b87](https://github.com/samosvalishe/turn-proxy-android/commit/85e7b87b9c5d3c1bc7232b1c013484d719ebd1ca))
* **logs:** маскировка аргументов ядра под приватным режимом ([8846e57](https://github.com/samosvalishe/turn-proxy-android/commit/8846e573cb05939599606bb7f595b64f7c93b556))
* **split:** убрана загрузка пресета РФ ([a30625f](https://github.com/samosvalishe/turn-proxy-android/commit/a30625f4a88daef57f2505f9655795613b99eef6))


### Fixes

* **backup:** восстановление заменяет профиль целиком ([93e1fc6](https://github.com/samosvalishe/turn-proxy-android/commit/93e1fc68bae14995d9ca84b7519dcb3bbb0f60d1))
* **captcha:** исправлена работоспособность автосолвера ([f0d0db3](https://github.com/samosvalishe/turn-proxy-android/commit/f0d0db3e5f1b2c0d6a5a0c0fcf07b6b00a8546df))
* **haptics:** вибра не глушится legacy-ключом системы ([e90c5f6](https://github.com/samosvalishe/turn-proxy-android/commit/e90c5f67709dd7d1576f6ac80dc637ea71def3fe))
* **proxy:** не ждать 5 минут при падении старта FGS ([bd48667](https://github.com/samosvalishe/turn-proxy-android/commit/bd486671ac1082975c6d52b19243dc565ffd69f1))
* **ssh:** StrictHostKeyChecking=yes для блокировки MITM ([ece9090](https://github.com/samosvalishe/turn-proxy-android/commit/ece90902f731a2db2efda676d33ee209cd36237e))
* **ui:** плавный переход состояний главной кнопки ([7708415](https://github.com/samosvalishe/turn-proxy-android/commit/77084151f649db1d02f9beb8a76f450b3a47529a))
* **wg:** MTU 1280 константой транспорта + MSS clamp на сервере ([f208597](https://github.com/samosvalishe/turn-proxy-android/commit/f208597cdea3c096e94a026ea3524eb5940d0650))
* временный блок socks 5 hotspot ([1460bfb](https://github.com/samosvalishe/turn-proxy-android/commit/1460bfb59f6f749e2dbb3be08b04f4d7a50e69f5))

## [3.4.2](https://github.com/samosvalishe/turn-proxy-android/compare/v3.4.1...v3.4.2) (2026-07-13)


### Fixes

* **captcha:** -platform mobile и WebView для ручной капчи ([e3ca640](https://github.com/samosvalishe/turn-proxy-android/commit/e3ca6407396f9c137a3ed9e357e63fec3cd6cd2c))

## [3.4.1](https://github.com/samosvalishe/turn-proxy-android/compare/v3.4.0...v3.4.1) (2026-07-12)


### Fixes

* **captcha:** адаптировать авторешение под SPA-капчу VK, обновление бинарника ([8c06c7e](https://github.com/samosvalishe/turn-proxy-android/commit/8c06c7ef99a530d8cccbea57e3e51ed7180357cb))
* формат телеграм поста ([bc9e459](https://github.com/samosvalishe/turn-proxy-android/commit/bc9e459b08cae003d6694c8e5d9eb39420c11cbf))

## [3.4.0](https://github.com/samosvalishe/turn-proxy-android/compare/v3.3.2...v3.4.0) (2026-07-08)


### Features

* add Russian preset and .srs rules to split tunneling ([9383401](https://github.com/samosvalishe/turn-proxy-android/commit/9383401188e3e65fc685c63f6ad3e66bb1c4b2d0))
* обновление бинарников ([28fed63](https://github.com/samosvalishe/turn-proxy-android/commit/28fed63359894d0c076fbf9856b779fc0092fbe4))


### Fixes

* **server:** диагностика скипов рестарта и гонка перезапуска прокси ([8e56023](https://github.com/samosvalishe/turn-proxy-android/commit/8e560234492d495191b20c09c657f0cf6efd0718))
* unit ([2e310b2](https://github.com/samosvalishe/turn-proxy-android/commit/2e310b27155a349a99dac8b90dd9932753f96395))
