# Monet theme picker for PXLNET Connect 0.6.5-beta

## Scope

Improve only the existing appearance dialog. Keep the existing theme persistence, six fixed accents, and System/Light/Dark mode. No backend or VPN behavior changes.

## Interaction

- Show a separate “Colors from wallpaper” switch, with a short explanation and an Android 11-and-older fallback note.
- Below it, show six horizontally scrollable palette preview tiles (green, sky, violet, coral, amber, rose). Each tile has a clear selected state and accessible color name.
- Choosing a fixed tile turns wallpaper colors off. Turning wallpaper colors off with the switch restores the last fixed tile selected in this dialog, defaulting to green if none was selected.
- Theme mode remains an independent System/Light/Dark choice.
- Changes apply immediately through the existing `Settings.accent` and theme-mode state; dismissing the dialog does not reset them.

## Compatibility and checks

On Android 12+, wallpaper mode uses the existing dynamic Material color scheme. On older Android versions, it retains the existing PXLNET fallback. The picker must fit narrow screens and remain usable with screen-reader labels and touch targets. Build the Android variant and exercise theme persistence where automated checks allow; visual device testing remains for the user.
