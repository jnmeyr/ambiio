let app = new Vue({
  el: '#app',
  data: {
    lastCommand: {},
    color: '#ff00ff',
    duration: '10 milliseconds',
    server: 'tcp://localhost:1883',
    topic: 'ambiio'
  },
  watch: {
    lastCommand: function(lastCommand) {
      if (lastCommand.hasOwnProperty('Frequencies')) {
        let frequencies = lastCommand['Frequencies'];
        this.color = frequencies.inOpt || this.color;
        this.duration = frequencies.everyOpt || this.duration;
      } else if (lastCommand.hasOwnProperty('Glow')) {
        let glow = lastCommand['Glow'];
        this.color = glow.inOpt || this.color;
      } else if (lastCommand.hasOwnProperty('Loudness')) {
        let loudness = lastCommand['Loudness'];
        this.color = loudness.inOpt || this.color;
        this.duration = loudness.everyOpt || this.duration;
      } else if (lastCommand.hasOwnProperty('Pulse')) {
        let pulse = lastCommand['Pulse'];
        this.color = pulse.inOpt || this.color;
        this.duration = pulse.everyOpt || this.duration;
      } else if (lastCommand.hasOwnProperty('Telemetry')) {
        let telemetry = lastCommand['Telemetry'];
        this.server = telemetry.server;
        this.topic = telemetry.topic;
      }
    }
  },
  methods: {
    getLastCommand: function() {
      fetch('command')
        .then(response => response.json())
        .then(json => this.lastCommand = json)
        .catch(error => console.log(error));
    },
    setNextCommand: function(event) {
      var command;
      if (event.target.innerHTML === 'Frequencies') {
        command = {
          'Frequencies': {
            'everyOpt': this.duration,
            'inOpt': this.color
          }
        }
      } else if (event.target.innerHTML === 'Glow') {
        command = {
          'Glow': {
            'inOpt': this.color
          }
        }
      } else if (event.target.innerHTML === 'Loudness') {
        command = {
          'Loudness': {
            'everyOpt': this.duration,
            'inOpt': this.color
          }
        }
      } else if (event.target.innerHTML === 'Pause') {
        command = {
          'Pause': {}
        }
      } else if (event.target.innerHTML === 'Pulse') {
        command = {
          'Pulse': {
            'everyOpt': this.duration,
            'inOpt': this.color
          }
        }
      } else if (event.target.innerHTML === 'Telemetry') {
        command = {
          'Telemetry': {
            'server': this.server,
            'topic': this.topic
          }
        }
      }
      let request = new XMLHttpRequest();
      request.open('POST', 'command');
      request.onreadystatechange = function () {
        app.getLastCommand();
      };
      request.send(JSON.stringify(command));
    }
  },
  mounted () {
    this.getLastCommand();
  },
  computed: {
    isFrequencies: function () {
      return this.lastCommand['Frequencies'] !== undefined;
    },
    isGlow: function () {
      return this.lastCommand['Glow'] !== undefined;
    },
    isLoudness: function () {
      return this.lastCommand['Loudness'] !== undefined;
    },
    isPause: function () {
      return this.lastCommand['Pause'] !== undefined;
    },
    isPulse: function () {
      return this.lastCommand['Pulse'] !== undefined;
    },
    isTelemetry: function () {
      return this.lastCommand['Telemetry'] !== undefined;
    },
    needsColor: function () {
      if (this.isFrequencies) { return true; }
      else if (this.lastCommand['Glow'] !== undefined) { return true; }
      else if (this.lastCommand['Loudness'] !== undefined) { return true; }
      else if (this.lastCommand['Pulse'] !== undefined) { return true; }
      else if (this.lastCommand['Telemetry'] !== undefined) { return false; }
      else { return false };
    },
    needsDuration: function () {
      if (this.lastCommand['Frequencies'] !== undefined) { return true; }
      else if (this.lastCommand['Glow'] !== undefined) { return false; }
      else if (this.lastCommand['Loudness'] !== undefined) { return true; }
      else if (this.lastCommand['Pulse'] !== undefined) { return true; }
      else if (this.lastCommand['Telemetry'] !== undefined) { return false; }
      else { return false };
    }
  }
});
