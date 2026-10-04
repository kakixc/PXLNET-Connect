# PXLNet Connect

## 📥 Скачать PXLNET Connect

[![Universal](https://img.shields.io/badge/Download-Universal-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://github.com/kakixc/PXLNET-Connect/releases/download/v0.6.5-beta/PXLNET-Connect-0.6.5-beta-universal.apk)

[![ARM64-v8a](https://img.shields.io/badge/Download-ARM64--v8a-181717?style=for-the-badge&logo=android&logoColor=white)](https://github.com/kakixc/PXLNET-Connect/releases/download/v0.6.5-beta/PXLNET-Connect-0.6.5-beta-arm64-v8a.apk)

**Universal** — выбирайте, если не знаете архитектуру устройства.  
**ARM64-v8a** — для большинства современных Android-смартфонов, сборка немного компактнее.

[Все релизы](https://github.com/kakixc/PXLNET-Connect/releases)

**PXLNet Connect** — Android VPN-клиент на базе sing-box для сервиса PXLNet.

Поддерживает VLESS, Hysteria2, Smart Routing, автоматический выбор серверов,
мониторинг состояния узлов и интеграцию с аккаунтом PXLNet.

> Сейчас проект находится в Beta.

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
- одноразовый вход через `@pxlnet_bot` без пароля и без токена бота в APK;
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
- поиск приложений, массовый выбор, локальные исключения и хранение списка только на устройстве;
- контекстный главный экран: действия импорта и добавления плитки скрываются, когда уже не нужны;
- русский интерфейс для экранов PXLNET и английский fallback для остальных системных языков;
- фирменная монохромная иконка PXLNET в системном VPN-уведомлении;
- импорт нативной конфигурации sing-box сохранён для расширенного режима.

## Быстрый тест на телефоне

1. Установить arm64 APK из `app/build/outputs/apk/other/debug/`.
2. Открыть приложение и нажать добавление профиля.
3. Выбрать «Добавить подписку», вставить URL и сохранить.
4. Нажать большую кнопку запуска и подтвердить системный запрос Android на создание VPN.
5. После подключения открыть карточку группы PXLNET: там доступны AUTO, серверы с флагами и проверка задержки.

Debug APK подписан стандартным отладочным ключом и предназначен только для beta-теста. Ссылки подписок в исходники и APK не вшиваются.

## Сборка

Для приложения нужны Android SDK (API 37.1), NDK 28.0.13004108, Java 17 и Go 1.25.12. `app/libs/libbox.aar` и `libbox-legacy.aar` собираются для четырёх ABI: armeabi-v7a, arm64-v8a, x86 и x86_64. Иначе файл с названием `universal.apk` может не работать на части устройств.

Ядро для текущей Beta воспроизводится из sing-box `v1.14.0-beta.7`:

```powershell
git clone --branch v1.14.0-beta.7 https://github.com/SagerNet/sing-box.git third_party/sing-box
git -C third_party/sing-box apply ../../patches/sing-box-pxlnet.patch
$env:GOROOT = 'путь-к-Go-1.25.12'
$env:ANDROID_HOME = 'путь-к-Android-SDK'
$env:ANDROID_NDK_HOME = Join-Path $env:ANDROID_HOME 'ndk/28.0.13004108'
$env:JAVA_HOME = 'путь-к-JDK-17'
$env:GOPATH = Join-Path (Get-Location).Path '.build-tools/gopath'
$env:GOCACHE = Join-Path (Get-Location).Path '.build-tools/gocache'
$env:GOTELEMETRY = 'off'
$env:GOTOOLCHAIN = 'local'
$env:PATH = "$env:GOROOT\bin;$env:GOPATH\bin;$env:PATH"
& "$env:GOROOT\bin\go.exe" install github.com/sagernet/gomobile/cmd/gomobile@v0.1.12 github.com/sagernet/gomobile/cmd/gobind@v0.1.12
.\scripts\build-libbox.ps1
```

Текущий закреплённый sing-box `v1.14.0-beta.7` **не поддерживает XHTTP**: проверка конфигурации возвращает `unknown transport type: xhttp`. Connect теперь явно сообщает об этом при импорте VLESS-XHTTP вместо тихой подмены обычным TCP. Для настоящей поддержки нужен отдельно согласованный совместимый движок; изменения серверного inbound не входят в эту сборку.

```powershell
.\gradlew.bat :app:testOtherDebugUnitTest
.\gradlew.bat :app:assembleOtherDebug
```

## Происхождение и лицензия

PXLNET Connect — переработанный и переименованный форк официального [sing-box for Android](https://github.com/SagerNet/sing-box-for-android) с ядром [sing-box](https://github.com/SagerNet/sing-box). Пользовательский интерфейс и часть продуктовых идей вдохновлены открытым проектом [Happ Android](https://github.com/Happ-proxy/happ-android); PXLNET Connect не является официальной сборкой Happ и не заявляет связь с его авторами. Исходные проекты и этот форк распространяются по GPLv3; полный текст и дополнительное ограничение на использование исходного имени находятся в `LICENSE`. PXLNET Connect не заявляет связь или одобрение со стороны SagerNet.

Новости сервиса публикуются в [Telegram-канале @pxlnet](https://t.me/pxlnet), поддержка и вход в аккаунт доступны через [@pxlnet_bot](https://t.me/pxlnet_bot).

В PXLNET Connect отсутствуют функции сокрытия VPN от других приложений и сервисов. Xposed/VPN-hide компоненты исходного форка удалены из кода и сборки.
