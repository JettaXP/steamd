# steamd

Тема в стиле Material 3 для Android-приложения Steam.

> Неофициальный проект, не связан с Valve. Скрипт меняет только вашу собственную копию приложения.
> В репозитории нет файлов Steam. Не распространяйте пропатченные APK.

## Возможности
- **Палитра M3** для нативных экранов: меню, Steam Guard, навигация, шапки.
- **Свой шрифт** вместо Motiva Sans, например Roboto.
- **Тема для магазина и новостей** *(в разработке)*. CSS подгружается из `theme/m3.css`, и его можно обновлять без пересборки приложения.

Скрипт меняет только цвета и шрифты. Расположение элементов на экранах остаётся прежним.

Проверено на Steam **3.10.11**. На других версиях скрипт сообщит, если не найдёт нужные данные.

## Перед установкой
Чтобы поставить пропатченное приложение, официальное нужно удалить, а вместе с ним удаляются данные Steam Guard.
**Сначала запишите код восстановления** (Steam → Steam Guard → «Код восстановления»).

## Требования
- Python 3.10+: `pip install hermes-dec fonttools`
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

# 3. Подписать все APK одним ключом
cp apk/split_*.apk out/
java -jar uber-apk-signer.jar --apks out --allowResign -o signed

# 4. Заменить официальное приложение (после того как записали код восстановления!)
adb uninstall com.valvesoftware.android.steam.community
adb install-multiple signed/*.apk
```
После установки войдите в аккаунт и заново привяжите Steam Guard.

## Настройка
Цвета задаются в словаре `PALETTE` в начале `patch/patch_steam.py`, формат `#RRGGBB`.
Проверить бандл без сборки APK:
```sh
python patch/patch_steam.py --check index.android.bundle
```

## Если что-то не работает
- **Приложение не запускается.** Соберите лог (`adb logcat -d > log.txt`) и откройте issue.
- **Цвета вернулись к стандартным.** Приложение скачало обновление поверх патча, тоже создайте issue.
- **Откат.** `adb uninstall com.valvesoftware.android.steam.community`, затем установите Steam из Google Play.

## Как это работает
Интерфейс приложения написан на React Native (Hermes). Встроенная тема хранится в строковой таблице байткода.
Скрипт перенаправляет ссылки на цвета на новые значения и пересчитывает контрольную сумму.
Байткод при этом не меняется. Шрифты Motiva Sans заменяются файлами того же начертания.

## Благодарности
- [kuska1/Material-Theme](https://github.com/kuska1/Material-Theme) (MIT), тема для Millennium, на которой основана тема магазина.
- [hermes-dec](https://github.com/P1sec/hermes-dec), разбор байткода Hermes.

## Лицензия
MIT
