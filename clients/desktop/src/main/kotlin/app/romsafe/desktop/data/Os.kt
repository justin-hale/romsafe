package app.romsafe.desktop.data

/** The desktop OS this runs on; anything that isn't Windows or macOS is treated as Linux. */
enum class Os(val wire: String) {
    Windows("windows"),
    MacOs("macos"),
    Linux("linux"),
    ;

    companion object {
        val current: Os = System.getProperty("os.name").lowercase().let {
            when {
                it.startsWith("windows") -> Windows
                it.contains("mac") -> MacOs
                else -> Linux
            }
        }
    }
}
