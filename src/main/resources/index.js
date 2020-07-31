let app = new Vue({
  el: '#app',
  data: {
    lastCommand: {},
    duration: '10ms',
    color: '#ff00ff',
    server: 'tcp://localhost:1883',
    topic: 'ambiio'
  },
  methods: {
    getLastCommand: function() {
      fetch('command')
        .then(response => response.json())
        .then(json => this.lastCommand = json)
        .catch(error => console.log(error));
    },
    setNextCommand: function(event) {
      let request = new XMLHttpRequest();
      request.open('POST', 'command');
      request.onreadystatechange = function () {
        app.getLastCommand();
      };
      request.send(JSON.stringify(
        {
          [event.target.value]: {
            'everyOpt': this.duration,
            'inOpt': this.color
          }
        }
      ));
    },
    setTele: function(event) {
      let request = new XMLHttpRequest();
      request.open('POST', 'command');
      request.onreadystatechange = function () {
        app.getLastCommand();
      };
      request.send(JSON.stringify(
        {
          [event.target.value]: {
            'server': this.server,
            'topic': this.topic
          }
        }
      ));
    }
  },
  mounted () {
    this.getLastCommand();
  }
});
