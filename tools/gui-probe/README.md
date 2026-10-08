# GUI probe: see the booth screen, and prove a change did not move anything

A change to the booth screen cannot be judged from the code: where a control lands depends on the GUI
scale, the window size and the art underneath it. This probe builds a booth in a throwaway world, opens
the screen and takes real screenshots from the dev client, so two builds can be compared pixel by pixel.

It is **not part of the mod**. `GuiProbe.java.txt` has a `.txt` extension so nothing compiles it, and it
must never be committed as a `.java` file or shipped in a jar.

## Use

1. Copy the dev world once: `neoforge/run/saves/New World` to `neoforge/run/saves/ProbeWorld` (any
   existing singleplayer world works; it is only built on top of).
2. Copy `GuiProbe.java.txt` to `common/src/main/java/com/osgworld/djbooth/client/devprobe/GuiProbe.java`
   and add this as the last line of `DJBoothClient.init()`:
   `com.osgworld.djbooth.client.devprobe.GuiProbe.init();`
3. Take the baseline, then make the change, then take it again:

   ```
   SOUNDSYSTEM_PROBE=before ./gradlew :neoforge:runClient --args="--quickPlaySingleplayer ProbeWorld --width 1600 --height 900"
   SOUNDSYSTEM_PROBE=after  ./gradlew :neoforge:runClient --args="--quickPlaySingleplayer ProbeWorld --width 1600 --height 900"
   ```

   The first run needs network access (Architectury downloads an agent), so no `--offline`.
   Screenshots land in `neoforge/run/screenshots/`. The game closes itself.
4. Compare: `python tools/gui-probe/diff_gui.py before_2_gui_1.png after_2_gui_1.png diff.png`. It lists the
   regions that changed and writes a copy of the new screenshot with them boxed. Every region should be
   somewhere you meant to change; anything else is a control that moved.
5. **Remove the probe and the `init()` line before committing.**

## Gallery shots

A deck only draws its waveform and times once a track has opened, so a gallery shot needs a real link.
What worked, and what did not:

- **Worked:** a track made here (`make_demo_track.py`, a synthesised four-on-the-floor loop, so nothing to
  license) served from this machine by `serve_audio.py`, which answers HTTP Range requests because the
  player seeks by byte range. Run both once, then:

  ```
  python tools/gui-probe/make_demo_track.py
  python tools/gui-probe/serve_audio.py &        # stops itself after 15 minutes without a request
  SOUNDSYSTEM_PROBE=gallery SOUNDSYSTEM_PROBE_GALLERY=1 \
      SOUNDSYSTEM_PROBE_URL=http://127.0.0.1:8765/demo-set.wav \
      ./gradlew :neoforge:runClient --args="--quickPlaySingleplayer ProbeWorld --width 2400 --height 1350"
  ```

  The game is muted for the capture and its volume put back afterwards. A window of that size gives a
  panel large enough that a crop of the mixer or one deck is sharp. The URL boxes show the local link.
- **Did not work:** a CC0 track straight from Wikimedia Commons. WaterMedia refuses a link whose server
  sends `application/ogg` ("Content is not multimedia"), and Commons' MP3 copy answered 429 Too Many
  Requests to FFmpeg. A direct link only plays if the server sends an audio or video content type and
  does not throttle the player.

## What it cannot do

It sees one fixed state at one window size. It does not click anything, and it does not replace opening
the game and trying the controls.
