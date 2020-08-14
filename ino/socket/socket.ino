#include <FastLED.h>
#include <WiFiUdp.h>
#include <ESP8266WiFi.h>

#define LED_PIN 0
#define NUM_LEDS 60
#define PROTOCOL_SIZE 2
#define LED_SIZE 3
#define BUFFER_SIZE PROTOCOL_SIZE + NUM_LEDS * LED_SIZE

WiFiUDP udp;

int bytes;
char buffer[BUFFER_SIZE];

CRGB leds[NUM_LEDS];

void setup() {
  FastLED.addLeds<NEOPIXEL, LED_PIN>(leds, NUM_LEDS);
  FastLED.clear(leds);
  WiFi.mode(WIFI_STA);
  WiFi.begin("", "");
  while (WiFi.status() != WL_CONNECTED) {
    delay(100);
  }
  udp.begin(12345);
}

void loop() {
  bytes = udp.parsePacket();
  if (bytes) {
    udp.read(buffer, BUFFER_SIZE);
    if (buffer[0] == 's' && buffer[1] == 'O' && bytes == PROTOCOL_SIZE + NUM_LEDS * LED_SIZE) { // sO // set one [$rgb]
      memcpy((char*) leds, buffer + PROTOCOL_SIZE, NUM_LEDS * LED_SIZE);
      FastLED.show();
    }
  }
}
