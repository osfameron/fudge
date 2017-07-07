# Minimal logging library

## Principles:

- https://12factor.net/logs
- single appender, e.g. STDOUT is trivial.
 (Everything else can be built atop this if you think you need it
  (but you probably don't))
- functions all the way down
- just Clojure data-structures
- trivial to create appenders (e.g. like Timbre docs claim it is)
- play nicely with pipeline style (like timbre's spy)

## Secondary concerns:

- compiling out hidden log-levels with macros
- tracing/line numbers and such
- dynamic reloading of config

## Explicitly NOT design concerns

- multiple appenders (just provide a map as your function)
- Java logging (your appender can interface if needed)
- writing to files (>> /var/log/foo.log or add to function)
- logrotation (logrotate.d or add to appender function)
- filtering (grep - or custom valid-function)
- complicated dispatch
