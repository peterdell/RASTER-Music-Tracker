RASTER Music Tracker - Java version
===================================

Keep this folder together and start the program from it.

  rmt.exe / rmt / rmt.app   the program
  songs                     the example songs, and where your own go
  instruments               the instruments
  exports                   finished tunes exported by older RMT versions
  rmt.ini, tuning.ini       your settings, written when you change them

The program needs no installation and no Java: everything it needs is in
this folder. Move the whole folder wherever you like, including onto a
USB stick.

macOS: do not drag rmt.app out of this folder on its own. It would still
start, but it would leave your songs, instruments and settings behind,
and the settings would then be kept in
~/Library/Application Support/RMT instead. Help > About always shows the
two folders the program is actually using.

Replacing an Atari driver for testing
-------------------------------------

The player routines live inside the program, under resources/drivers and
resources/players. To try a different build of a driver, put the file
there under the same name and restart the program.

KEEP A COPY OF THE ORIGINAL FIRST. These are the files RMT ships, so
writing over one replaces it, and deleting it leaves none - RMT then
stays silent and exports no sound, and says so when it starts.

  Windows   rmt\app\resources\drivers
  Linux     rmt/lib/app/resources/drivers
  macOS     rmt.app/Contents/app/resources/drivers
            (right-click rmt.app, "Show Package Contents"; note that
            changing anything inside the bundle breaks its signature,
            so macOS may refuse to start it afterwards)

Documentation
-------------

The manual and the change history are inside the program folder, under
docs; Help > Help Topics opens them. The sources, the issue tracker and
the Windows version are at

  https://github.com/raster-atari-org/RASTER-Music-Tracker
