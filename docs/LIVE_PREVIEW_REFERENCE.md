# Live TV preview reference

## Firsthand TiviMate inspection

- Device: onn. Streaming Device 4K Pro, ADB `<onn-4k-pro-box>`,
  hardware serial `<box-serial>`.
- TiviMate 5.0.1, inspected on 2026-10-31 using remote-equivalent ADB input.
- The TV section combines a category list with a vertically scrollable channel
  and programme grid. The expanded guide uses the same kind of grid; it was
  not observed as a separate plain channel-list screen.
- In the expanded guide, the preview was approximately 640 x 360 pixels at the
  upper left of a 1920 x 1080 display. Programme title, time, progress, group,
  and description appeared alongside it; the timeline and rows were below.
  Opening the category list shifted the preview and grid to the right.
- Moving focus changed the selected programme and its description, but the
  existing preview kept playing. Selecting a different live channel once
  switched the preview and its playing-channel marker without closing the
  grid. A second selection of the same channel, after an eight-second pause,
  opened full-screen playback. Back returned to the grid with the preview,
  selection, and visible guide position restored. Playback continued while
  the category list was opened and another category was selected.
- The inspected settings were Preview On, Animated transition On, Autoplay
  channels Off, Stay on TV guide when switching channels On, and Full screen
  switching timeout 120 seconds. No TiviMate settings were changed. After
  waiting more than 120 seconds on a preview, TiviMate had automatically
  switched to full screen. YourIPTV deliberately does not copy that timeout:
  the requested interaction requires a separate second selection, regardless
  of elapsed time.
- An initial channel failed with an HTTP data-source error. Selecting another
  channel subsequently produced actual video; selecting the A&E channel
  switched the preview to different live video, with one active audio track.

Evidence on this computer is under `qa_evidence\tivimate_preview\`:
`t10_channel_focus.png`, `t11_ok1.png`, `t12_ok2.png`, `t13_back_full.png`,
`t14_focus_only.png`, `t15_switch_preview.png`, `t16_group_focus.png`,
`t17_category.png`, `t23_guide_options.png`, `t24_preview_modes.png`,
`t26_another_focus.png`, `t27_second_channel.png`, `t28_waited.png`, and
`t29_waited_ok.png`.

## YourIPTV comparison

YourIPTV 0.2.0 placed the category list beside a scrollable channel/programme grid in Live TV,
with TV Guide as its own screen. From 0.3.0 the two are one TiviMate-style flow (menu →
categories → guide); see RELEASE_NOTES.md. The views keep the preview while browsing, do not tune
on focus alone, and use a later selection of the playing channel for full screen. Back restores
the originating view and its selection. Unlike the inspected TiviMate configuration, YourIPTV
does not automatically enter full screen after a timeout.

On-device comparison results and their limitations are recorded in [QA_CHECKLIST.md](QA_CHECKLIST.md).
