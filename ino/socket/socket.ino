#include <FastLED.h>
#include <WiFiUdp.h>
#include <ESP8266WiFi.h>

#define LED_PIN 0
#define NUM_LEDS 56
#define PROTOCOL_SIZE 2
#define LED_SIZE 3
#define BUFFER_SIZE PROTOCOL_SIZE + NUM_LEDS * LED_SIZE

WiFiUDP udp;

int bytes;
char buffer[BUFFER_SIZE];

CRGB leds[2 + NUM_LEDS];

void setup() {
  FastLED.addLeds<NEOPIXEL, LED_PIN>(leds, NUM_LEDS);
  FastLED.clear(leds);
  leds[0] = CRGB::Red;
  FastLED.show();
  WiFi.mode(WIFI_STA);
  WiFi.begin("", "");
  while (WiFi.status() != WL_CONNECTED) {
    leds[0] = CRGB::Yellow;
    FastLED.show();
    delay(100);
  }
  udp.begin(12345);
  leds[0] = CRGB::Green;
  FastLED.show();
  delay(1000);
}

void loop() {
  bytes = udp.parsePacket();
  if (bytes) {
    udp.read(buffer, BUFFER_SIZE);
    if (buffer[0] == 's' && buffer[1] == 'O' && bytes == PROTOCOL_SIZE + NUM_LEDS * LED_SIZE) { // sO // set one [$rgb]
      memcpy(((char*) leds) + 2 * LED_SIZE, buffer + PROTOCOL_SIZE, NUM_LEDS * LED_SIZE);
      FastLED.show();
    }
  }
}
