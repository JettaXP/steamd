# steamd

Тема в стиле Material 3 для Android-приложения Steam.

Сайт проекта: **https://steamd.jettax.xyz**

> Неофициальный проект, не связан с Valve. Скрипт меняет только вашу собственную копию приложения.
> В репозитории нет файлов Steam. Не распространяйте пропатченные APK.

## Возможности
- **Палитра M3** для нативных экранов: меню, Steam Guard, навигация, шапки.
- **Свой шрифт** вместо Motiva Sans, например Roboto.
- **Тема для магазина и новостей.** Приложение подгружает [`theme/m3.css`](theme/m3.css) с сайта проекта, поэтому тему можно обновлять без пересборки.
- **Свой цвет (Material You).** `--seed "#RRGGBB"` генерирует всю палитру из одного цвета, как в Android 12+. Подобрать цвет можно на [сайте](https://steamd.jettax.xyz/#color).

Скрипт меняет только цвета и шрифты. Расположение элементов на экранах остаётся прежним.

Проверено на Steam **3.10.11**. На других версиях скрипт сообщит, если не найдёт нужные данные.

## Перед установкой
Чтобы поставить пропатченное приложение, официальное нужно удалить, а вместе с ним удаляются данные Steam Guard.
**Сначала запишите код восстановления** (Steam → Steam Guard → «Код восстановления»).

## Требования
- Python 3.10+: `pip install hermes-dec fonttools materialyoucolor`
- Java 11+ и [uber-apk-signer](https://github.com/patrickfav/uber-apk-signer/releases)
- [Android platform-tools](https://developer.android.com/tools/releases/platform-tools) (adb)
- Статические TTF-шрифты для замены, например [Roboto](https://fonts.google.com/specimen/Roboto) (папка `static/` из архива). Variable-шрифты не подойдут.

## Установка
```sh
# 1. Скачать все APK приложения: base и split
adb shell pm path com.valvesoftware.android.steam.community
adb pull <каждый путь из вывода> apk/

# 2. Пропатчить base.apk
python patch/patch_steam.py apk/base.apk out/base.apk --fonts fonts/
#    свой цвет: добавьте --seed "#6750A4"

# 3. Подписать все APK одним ключом
cp apk/split_*.apk out/
java -jar uber-apk-signer.jar --apks out --allowResign -o signed

# 4. Заменить официальное приложение (после того как записали код восстановления!)
adb uninstall com.valvesoftware.android.steam.community
adb install-multiple signed/*.apk
```
После установки войдите в аккаунт и заново привяжите Steam Guard.

## Настройка
| Параметр | Что делает |
|---|---|
| `--seed "#RRGGBB"` | палитра Material You из цвета-источника |
| `--fonts DIR` | замена Motiva Sans статическими TTF из папки |
| `--css-url URL` | свой CSS для магазина и новостей (https) |
| `--no-store-theme` | не подключать CSS в магазине и новостях |
| `--no-colors` | оставить родные цвета приложения |
| `--steam-icons` | оставить родные иконки нижней панели |

Без `--seed` используется палитра `PALETTE` в начале `patch/patch_steam.py`.
Проверить бандл без сборки APK:
```sh
python patch/patch_steam.py --check index.android.bundle
```

## Если что-то не работает
- **Приложение не запускается.** Соберите лог (`adb logcat -d > log.txt`) и откройте issue.
- **Цвета вернулись к стандартным.** Приложение скачало обновление поверх патча, тоже создайте issue.
- **Откат.** `adb uninstall com.valvesoftware.android.steam.community`, затем установите Steam из Google Play.

## Как это работает
Интерфейс приложения написан на React Native (Hermes), встроенная тема хранится в строковой таблице байткода.
Скрипт перенаправляет ссылки на цвета на новые значения и пересчитывает контрольную сумму, байткод при этом не меняется.

Магазин и новости открываются в WebView, куда приложение уже внедряет свой небольшой скрипт.
Патч заменяет его эквивалентом, который вдобавок задаёт цвет-источник (`--sd-p`) и подключает `theme/m3.css`.
Тема применяется только внутри приложения (`html.rn_mobile_app`), а все цвета в ней выводятся из `--sd-p`.

Шрифты Motiva Sans заменяются файлами того же начертания.

## Приложение для Android
В папке [`android/`](android/) — приложение на Kotlin, Jetpack Compose и Material 3, которое патчит Steam прямо на телефоне, без ПК: берёт цвета из обоев (Android 12+) или свой цвет, подписывает и устанавливает пропатченное приложение. Сейчас в разработке; тестовую сборку можно скачать в [GitHub Actions](https://github.com/JettaXP/steamd/actions) (артефакт `steamd-debug-apk`).

## Благодарности
- [kuska1/Material-Theme](https://github.com/kuska1/Material-Theme) (MIT), тема для Millennium, вдохновившая тему магазина.
- [hermes-dec](https://github.com/P1sec/hermes-dec), разбор байткода Hermes.
- [Google Sans](https://github.com/googlefonts/googlesans) (SIL OFL 1.1), шрифт для приложения Steam; статические начертания лежат в `android/app/src/main/assets/fonts`.
- [Material Symbols](https://github.com/google/material-design-icons) (Apache-2.0), иконки нижней панели Steam (залитая у активной вкладки, контурная у остальных); пути лежат в `patch/patch_steam.py`, лицензия в `patch/LICENSE-MaterialSymbols.txt`.
- [Material Web](https://github.com/material-components/material-web) (Apache-2.0), компоненты сайта. Сборка лежит в `assets/material-web.js`, лицензии сохранены в конце файла.
- [Material Color Utilities](https://github.com/material-foundation/material-color-utilities) (Apache-2.0), генерация палитры на сайте (`assets/color-utils.js`).

## Лицензия
MIT
