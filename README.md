# Ambiio #

An effect engine in Scala using cats-effect and optionally zio's IO monad.

## Version ##

0.6

## Arguments ##

* --controller.forever.command *command*: Command of the forever controller

* --controller.http.host *host*: Host of the http controller
* --controller.http.port *port*: Port of the http controller

* --controller.pipe.path *path*: Path of the pipe controller file

* --consumer.printer.pixels *pixels*: Number of pixels of the next printer consumer

* --consumer.serial.pixels *pixels*: Number of pixels of the next serial consumer
* --consumer.serial.every *every*: Frequency of the current serial consumer
* --consumer.serial.name *name*: Name of the current serial consumer

* --consumer.socket.pixels *pixels*: Number of pixels of the next socket consumer
* --consumer.socket.every *every*: Frequency of the current socket consumer
* --consumer.socket.host *host*: Host of the current socket consumer
* --consumer.socket.port *port*: Port of the current socket consumer

* --consumer.telemetry.pixels *pixels*: Number of pixels of the next telemetry consumer
* --consumer.telemetry.server *server*: Server of the current telemetry consumer
* --consumer.telemetry.topic *topic*: Topic of the current telemetry consumer

### Example ###
* `java -jar ambiio.jar --controller.pipe.path /tmp/ambiio --consumer.serial.pixels 60 --consumer.serial.every 25ms --consumer.serial.name /dev/ttyUSB0`

## Commands ##

* "frequencies [every *every*] [in #*rrggbb* | in *color*]"
* "glow [in #*rrggbb* | in *color*]"
* "loudness [every *every*] [in #*rrggbb* | in *color*]"
* "pause"
* "pulse [every *every*] [in #*rrggbb* | in *color*]"
* "telemetry of *topic* from *server* 

### Pipe ###

Using an argument like `--controller.pipe.path /tmp/ambiio` will use a pipe controller.

#### Examples ####
* `echo "frequencies every 10ms in #00ffff" > /tmp/ambiio`
* `echo "glow in red" > /tmp/ambiio`
* `echo "pause" > /tmp/ambiio`
* `echo "telemetry of ambiio from tcp://localhost:1883" > /tmp/ambiio`

### Http ###

Using an argument like `--controller.http.port 8080` will use a http controller.

#### Examples ####
* `curl -X POST -d "{ "Frequencies": { "everyOpt": "10ms", "inOpt": "#00ffff" } }" http://localhost:8080/command`
* `curl -X POST -d "{ "Glow": { "inOpt": "red" } }" http://localhost:8080/command`
* `curl -X POST -d "{ "Pause": {} }" http://localhost:8080/command`
* `curl -X POST -d "{ "Telemetry": { "server": "tcp://localhost:1883", "topic": "ambiio" } }" http://localhost:8080/command`

## License ##

MIT License

Copyright (c) 2020 Jan Meyer

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
