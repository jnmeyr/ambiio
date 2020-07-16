# Ambiio #

An effect engine in Scala using cats-effect.

## Version ##

0.1

## Arguments ##

* --controller.pipe.path *path*: Path of the pipe controller file

* --consumer.printer.pixels *pixels*: Number of pixels of the next printer consumer
* --consumer.printer.every *every*: Frequency of the current printer consumer in milliseconds

* --consumer.serial.pixels *pixels*: Number of pixels of the next serial consumer
* --consumer.serial.every *every*: Frequency of the current serial consumer in milliseconds
* --consumer.serial.name *name*: Name of the current serial consumer

* --consumer.socket.pixels *pixels*: Number of pixels of the next socket consumer
* --consumer.socket.every *every*: Frequency of the current socket consumer in milliseconds
* --consumer.socket.host *host*: Host of the current socket consumer
* --consumer.socket.port *port*: Port of the current socket consumer

### Example ###
* `java -jar ambiio.jar --controller.pipe.path /tmp/ambiio --consumer.serial.pixels 60 --consumer.serial.every 25ms --consumer.serial.name /dev/ttyUSB0`

## Commands ##

* "frequencies [every *every*] [in red green blue]"
* "glow [in red green blue]"
* "loudness [every *every*] [in red green blue]"
* "pause"
* "pulse [every *every*] [in red green blue]"
* "stop"

### Examples ###
* `echo "frequencies every 10 in 0 255 255" > /tmp/ambiio`
* `echo "pause" > /tmp/ambiio`
* `echo "glow in 255 0 0" > /tmp/ambiio`
* `echo "stop" > /tmp/ambiio`

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
