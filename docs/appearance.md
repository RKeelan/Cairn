# Cairn's appearance

Cairn's look is Slate and Sea: wet slate stones on a sea-green ground, like a cairn on a northern shore, with teal for what stands out. Both apps draw it from a fixed palette, light or dark as the system is, and do not take colours from the wallpaper.

## Icon

Four flat slate stones stacked by hand, each a little askew, getting smaller from a broad base to a pebble on top. From the base up the stones are #566870, #7F929A, #A8B8BE and #D2DDE0, on sea green #0E4643.

* The phone's icon is an adaptive icon. The stones sit inside the 66 dp circle in the middle of its 108 dp canvas, which every launcher mask shows.
* Its themed icon, which Android 13 and later draw in the system's colours, is the same stones in one colour, each raised a little so that a gap shows between them.
* The watch's icon is the same stones on a sea-green disc.

## Colour

The phone's colours are a pair of Material 3 `ColorScheme`s in `phone/.../ui/Theme.kt`; the roles below are the ones the screens use. Each gives the light value, then the dark.

* `background` and `surface`: the ground. Light #EEF3F2, dark #0E1615. The window shows the same colour until the first frame.
* `onSurface`: text. Light #172221, dark #D7E3E1.
* `onSurfaceVariant`: muted text, such as amounts, bought items and hints. Light #4F605E, dark #A3B5B2.
* `primary`: section labels, and what Material's components fill with their primary colour, such as buttons and ticked boxes. Light teal #0D6C66, dark #69CEC2.
* `surfaceContainerHighest`: the offline banner. Light #D5E0DE, dark #2A3634.
* `errorContainer`: the ground behind a row swiped away to delete it. Material's default red, light and dark.
* Nothing is tinted by its elevation: `surfaceTint` is the ground.

The watch is always dark, on a black ground, so its `ColorScheme`, in `wear/.../ui/Theme.kt`, takes the phone's dark values for the roles they share.
