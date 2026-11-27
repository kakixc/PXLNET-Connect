# PXLNet Connect

## 📥 Скачать PXLNET Connect

[![Universal](https://img.shields.io/badge/Download-Universal-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://github.com/kakixc/PXLNET-Connect/releases/download/v0.7.0/PXLNET-Connect-0.7.0-universal.apk)

[![ARM64-v8a](https://img.shields.io/badge/Download-ARM64--v8a-181717?style=for-the-badge&logo=android&logoColor=white)](https://github.com/kakixc/PXLNET-Connect/releases/download/v0.7.0/PXLNET-Connect-0.7.0-arm64-v8a.apk)

**Universal** — выбирайте, если не знаете архитектуру устройства.  
**ARM64-v8a** — для большинства современных Android-смартфонов, сборка немного компактнее.

[Все релизы](https://github.com/kakixc/PXLNET-Connect/releases)

**PXLNet Connect** — Android VPN-клиент на базе sing-box для сервиса PXLNet.

Поддерживает VLESS, Hysteria2, Smart Routing, автоматический выбор серверов,
мониторинг состояния узлов и интеграцию с аккаунтом PXLNet.

## Что уже работает

- настоящий VPN-туннель на базе sing-box;
- добавление удалённой подписки по URL без Telegram-входа;
- обычные и Base64-подписки с `vless://`, `hysteria2://` и `hy2://`;
- VLESS + TLS/Reality/uTLS и Hysteria2 + TLS/obfs;
- автоматическая группировка серверов, режим AUTO и ручной выбор узла;
- флаги Германии и Финляндии по названию сервера;
- замер задержки узлов, текущая скорость и объём трафика;
- чтение лимита и срока подписки из заголовка `subscription-userinfo`;
- автообновление подписки в фоне;
- вход в PXL ID по почте с одноразовым кодом. Вход через Telegram и привязка существующей подписки через Telegram доступны на экране аккаунта;
- гостевой режим по обычной ссылке подписки без обязательного аккаунта;
- единый раздел экосистемы: аккаунт, тариф, продление, новости, состояние сервисов и поддержка;
- отображение аккаунта, активности и срока подписки с автоматической синхронизацией профиля;
- PXL Guard: повторная проверка сбоя и переключение на доступный сервер при отказе выбранного узла;
- локальный экран состояния серверов и задержки, инструкция Always-on VPN;
- обезличенная отправка диагностики в Telegram с удалением UUID, токенов и адресов;
- быстрая проверка типовых ошибок и подготовка отчёта для поддержки;
- проверка новых версий через релизы PXLNET Connect на GitHub;
- простой пользовательский режим; Логи, Инструменты и расширенные настройки скрыты до включения режима разработчика;
- Smart Routing: локальная сеть, российские IP/домены и государственные сайты напрямую;
- три понятных режима: весь трафик через VPN, Smart Routing и только выбранные приложения;
- Expressive DNS с собственными DNS-профилями, отдельно или вместе с VPN;
- три темы интерфейса в разделе **Настройки → Персонализация** — «Классика», «Фокус» и «Пульт». Новая установка открывается в теме «Пульт»;
- поиск приложений, массовый выбор, локальные исключения и хранение списка только на устройстве;
- контекстный главный экран: действия импорта и добавления плитки скрываются, когда уже не нужны;
- русский интерфейс для экранов PXLNET и английский fallback для остальных системных языков;
- фирменная монохромная иконка PXLNET в системном VPN-уведомлении;
- импорт нативной конфигурации sing-box сохранён для расширенного режима.

## Быстрый тест на телефоне

1. Скачать ARM64-v8a или Universal APK кнопкой выше и установить поверх прежней бета-версии.
2. Открыть приложение. В RC.3 можно открыть «Аккаунт» и войти по почте, чтобы загрузить подписку из PXL ID.
3. Для гостевого подключения нажать **«+»**, вставить ссылку на сервер или подписку и сохранить.
4. Нажать большую кнопку запуска и подтвердить системный запрос Android на создание VPN.
5. Открыть «Выбор сервера». Для ручного узла можно проверить отклик VPN-сервера до подключения; AUTO остаётся автоматическим выбором и не имеет отдельного серверного пинга.

Официальные release APK устанавливаются поверх существующей версии Connect, если подпись совпадает и код уже установленной версии не выше кода скачанного APK. Ссылки подписок в исходники и APK не вшиваются.

## Сборка

Для сборки на Windows нужны Android SDK (API 37.1), NDK 28.0.13004108, JDK 21 и PowerShell 7. Для libbox используется Go 1.25.12. Нативные библиотеки для универсального APK собираются для четырёх ABI — armeabi-v7a, arm64-v8a, x86 и x86_64.

Из корня репозитория инициализируйте подмодуль Termux и соберите libbox из sing-box `v1.14.0-beta.7`.

```powershell
git submodule update --init --recursive
git clone --branch v1.14.0-beta.7 https://github.com/SagerNet/sing-box.git third_party/sing-box
git -C third_party/sing-box apply ../../patches/sing-box-pxlnet.patch
git -C third_party/sing-box apply ../../patches/sing-box-server-probe.patch
git -C third_party/sing-box apply ../../patches/sing-box-preconnect-probe.patch
$env:GOROOT = 'путь-к-Go-1.25.12'
$env:ANDROID_HOME = 'путь-к-Android-SDK'
$env:ANDROID_NDK_HOME = Join-Path $env:ANDROID_HOME 'ndk/28.0.13004108'
$env:JAVA_HOME = 'путь-к-JDK-21'
$env:GOPATH = Join-Path (Get-Location).Path '.build-tools/gopath'
$env:GOCACHE = Join-Path (Get-Location).Path '.build-tools/gocache'
$env:GOTELEMETRY = 'off'
$env:GOTOOLCHAIN = 'local'
$env:PATH = "$env:GOROOT\bin;$env:GOPATH\bin;$env:PATH"
& "$env:GOROOT\bin\go.exe" install github.com/sagernet/gomobile/cmd/gomobile@v0.1.12 github.com/sagernet/gomobile/cmd/gobind@v0.1.12
.\scripts\build-libbox.ps1
```

Для VLESS с транспортом XHTTP сборки `other` и `play` на Android 7.0+ используют Xray. Обычные подключения по-прежнему работают через sing-box. Сборка `otherLegacy` для Android 5 и 6 использует libbox без Xray.

Перед сборкой `other` или `play` соберите и положите Xray AAR в `app/libs`. Скрипт сборки Xray выбирает Go 1.26.0.

```powershell
pwsh -File .\third_party\xray\build-xray-aar.ps1 -GoExe "$env:GOROOT\bin\go.exe" -RunName connect-xray
Copy-Item .\.build-tools\xray-build\connect-xray\out\pxlnet-xray.aar .\app\libs\
```

Для локальной проверки соберите debug APK и запустите unit-тесты.

```powershell
.\gradlew.bat :app:testOtherDebugUnitTest
.\gradlew.bat :app:assembleOtherDebug
```

## Происхождение и лицензия

PXLNET Connect — переработанный и переименованный форк официального [sing-box for Android](https://github.com/SagerNet/sing-box-for-android) с ядром [sing-box](https://github.com/SagerNet/sing-box). Пользовательский интерфейс и часть продуктовых идей вдохновлены открытым проектом [Happ Android](https://github.com/Happ-proxy/happ-android); PXLNET Connect не является официальной сборкой Happ и не заявляет связь с его авторами. Исходные проекты и этот форк распространяются по GPLv3; полный текст и дополнительное ограничение на использование исходного имени находятся в `LICENSE`. PXLNET Connect не заявляет связь или одобрение со стороны SagerNet.

Новости сервиса публикуются в [Telegram-канале @pxlnet](https://t.me/pxlnet), поддержка и вход в аккаунт доступны через [@pxlnet_bot](https://t.me/pxlnet_bot).

В PXLNET Connect отсутствуют функции сокрытия VPN от других приложений и сервисов. Xposed/VPN-hide компоненты исходного форка удалены из кода и сборки.
