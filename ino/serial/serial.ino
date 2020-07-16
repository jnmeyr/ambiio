#include <FastLED.h>

#define LED_PIN 2
#define NUM_LEDS 60
#define PROTOCOL_SIZE 2
#define LED_SIZE 3
#define BUFFER_SIZE PROTOCOL_SIZE + NUM_LEDS * LED_SIZE

int bytes;
char buffer[BUFFER_SIZE];

CRGB leds[NUM_LEDS];

void setup() {
  Serial.begin(115200);
  Serial.setTimeout(100);
  FastLED.addLeds<NEOPIXEL, LED_PIN>(leds, NUM_LEDS);
  FastLED.clear(leds);
}

void loop() {
  bytes = 0;
  memset(buffer, 0, sizeof(buffer));
  if ((bytes = Serial.readBytes((char*) buffer, BUFFER_SIZE)) == BUFFER_SIZE) {
    if (buffer[0] == 's' && buffer[1] == 'O' && bytes == PROTOCOL_SIZE + NUM_LEDS * LED_SIZE) { // sO // set one [$rgb]
      memcpy((char*) leds, buffer + PROTOCOL_SIZE, NUM_LEDS * LED_SIZE);
      FastLED.show();
    }
  }
}
