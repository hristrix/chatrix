# Chatrix branding

`chatrix-logo.png` is the supplied Chatrix Chat Bubble Logo, preserved as provided. The mod's `assets/chatrix/icon.png` uses the same image.

`chatrix-icons.png` previews the matching white-and-violet interface icons. Editable SVG sources are under `icons/`. Icons use bold geometric shapes, clipped corners, transparent backgrounds, and a restrained purple accent.

The sheet contains four rows of four icons:

| Row | Icons, left to right |
| --- | --- |
| 1 | Settings, history, back, world |
| 2 | Messages, general settings, pin, host |
| 3 | Moderator, VIP, subscriber, Twitch |
| 4 | YouTube, Kick, TikTok, particle |

In-game textures keep their existing filenames and dimensions under `src/main/resources/assets/chatrix/textures/`.

Run `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/render-icons.ps1` from the project folder to regenerate the SVG sources, PNG textures, and preview from their shared geometric definitions.

The GUI uses the same violet (`#9425FA`) and off-white (`#FAFAFC`) palette over charcoal surfaces. `ModUiTheme` provides clipped-corner panels, buttons, inputs, slider tracks, and title accents; platform banners use the same styling. Native widgets retain their input and narration behavior.
