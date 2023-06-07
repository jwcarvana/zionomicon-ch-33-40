//> using scala "2.13.10"
//> using lib "org.scalameta::mdoc:2.3.7"

import mdoc.MainSettings
import java.nio.file.Paths

val settings: MainSettings = mdoc
  .MainSettings()
  .withIn(Paths.get("./slides.md"))
  .withOut(Paths.get("./slides-scala.md"))

mdoc.Main.process(settings)
