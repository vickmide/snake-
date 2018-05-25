var Console = {};

Console.log = (function (message) {
    var console = document.getElementById('console');
    var p = document.createElement('p');
    p.style.wordWrap = 'break-word';
    p.innerHTML = message;
    console.appendChild(p);
    while (console.childNodes.length > 25) {
        console.removeChild(console.firstChild);
    }
    console.scrollTop = console.scrollHeight;
});

let game;

class Snake {

    constructor() {
        this.snakeBody = [];
        this.color = null;
    }

    draw(context) {
        for (var pos of this.snakeBody) {
            context.fillStyle = this.color;
            context.fillRect(pos.x, pos.y,
                game.gridSize, game.gridSize);
        }
    }
}

class Game {

    constructor() {

        this.fps = 30;
        this.socket = null;
        this.nextFrame = null;
        this.interval = null;
        this.direction = 'none';
        this.gridSize = 10;

        this.skipTicks = 1000 / this.fps;
        this.nextGameTick = (new Date).getTime();
    }

    initialize() {

        this.snakes = [];
        let canvas = document.getElementById('playground');
        if (!canvas.getContext) {
            Console.log('Error: 2d canvas not supported by this browser.');
            return;
        }

        this.context = canvas.getContext('2d');
        window.addEventListener('keydown', e => {

            var code = e.keyCode;
            if (code > 36 && code < 41) {
                switch (code) {
                    case 37:
                        if (this.direction != 'east')
                            this.setDirection('west');
                        break;
                    case 38:
                        if (this.direction != 'south')
                            this.setDirection('north');
                        break;
                    case 39:
                        if (this.direction != 'west')
                            this.setDirection('east');
                        break;
                    case 40:
                        if (this.direction != 'north')
                            this.setDirection('south');
                        break;
                }
            }
        }, false);

        this.connect();
    }

    setDirection(direction) {
        this.direction = direction;
        var message = {
            "protocolo": "movement",
            "direction": direction
        };
        this.socket.send(JSON.stringify(message));
        Console.log('Sent: Direction ' + direction);
    }

    startGameLoop() {

        this.nextFrame = () => {
            requestAnimationFrame(() => this.run());
        }

        this.nextFrame();
    }

    stopGameLoop() {
        this.nextFrame = null;
        if (this.interval != null) {
            clearInterval(this.interval);
        }
    }

    draw() {
        this.context.clearRect(0, 0, 640, 480);
        for (var id in this.snakes) {
            this.snakes[id].draw(this.context);
        }
    }

    addSnake(id, color) {
        this.snakes[id] = new Snake();
        this.snakes[id].color = color;
    }

    updateSnake(id, snakeBody) {
        if (this.snakes[id]) {
            this.snakes[id].snakeBody = snakeBody;
        }
    }

    removeSnake(id) {
        this.snakes[id] = null;
        // Force GC.
        delete this.snakes[id];
    }

    run() {

        while ((new Date).getTime() > this.nextGameTick) {
            this.nextGameTick += this.skipTicks;
        }
        this.draw();
        if (this.nextFrame != null) {
            this.nextFrame();
        }
    }

    connect() {

        this.socket = new WebSocket("ws://127.0.0.1:8181/snake");

        this.socket.onopen = () => {

            // Socket open.. start the game loop.
            Console.log('Info: WebSocket connection opened.');
            Console.log('Info: Press an arrow key to begin.');

            this.startGameLoop();

            //setInterval(() => this.socket.send(JSON.stringify({"protocolo":"ping"})), 5000);
        }

        this.socket.onclose = () => {
            Console.log('Info: WebSocket closed.');
            this.stopGameLoop();
        }

        this.socket.onmessage = (message) => {

            console.log(message);
            var packet = JSON.parse(message.data);

            switch (packet.type) {
                case 'chat':
                    Console.log("Color Snake " + packet.data[0].color + ": " + packet.data[0].message);
                    break;
                case 'update':
                    for (var i = 0; i < packet.data.length; i++) {
                        this.updateSnake(packet.data[i].id, packet.data[i].body);
                    }
                    break;
                case 'join':
                    for (var j = 0; j < packet.data.length; j++) {
                        this.addSnake(packet.data[j].id, packet.data[j].color);
                    }
                    break;
                case 'leave':
                    this.removeSnake(packet.id);
                    break;
                case 'dead':
                    Console.log('Info: Your snake is dead, bad luck!');
                    this.direction = 'none';
                    break;
                case 'kill':
                    Console.log('Info: Head shot!');
                    break;
                case 'updateRooms':
                    for (var i = 0; i < packet.data.length; i++) {
                        Console.log(packet.data[i].id);
                        createRoom(packet.data[i].id);
                    }
                    break;
                case 'created':
                    createButton(packet.data[0]);
                    break;
            }


        }
    }
}

game = new Game();

game.initialize();

$(document).ready(function () {
    $('#message').keydown(function (event) {
        if (event.keyCode == 13) {

            var object = {
                "protocolo": "chat",
                message: $('#message').val()
            }
            document.getElementById('message').value = '';
            game.socket.send(JSON.stringify(object));

        }
    });

    function createRoom() {
       // var nombre = document.getElementById("nombresala");
        var message = {
            "protocolo": "create",
            "roomId": $('#nombresala').val()
        }
            
        document.getElementById('nombresala').value = '';
        console.log(JSON.stringify(message));        
        $("#lobby").css("display", "none");
        $("#game").css("display", "block");
        game.socket.send(JSON.stringify(message));
    }
    
    document.getElementById("create").addEventListener("click", createRoom);

})


function addRoomToLobby(roomname, numplayer, mode, started) {
    var elementExists = document.getElementById(roomname);
    Console.log(elementExists);
    if (elementExists == null) {
        var disabled = ""; 
        if (!started){
            numplayer = "Esperando a más jugadores..."
        }
        
        if (numplayer > 3){
            disabled = " disabled";
        }
        var card_list = document.getElementById('list');
        var div = document.createElement('div');
        div.id = "room_" + roomname;
        div.className = "card my-1 w-100";
        //.addEventListener("onClick", entrarEnSala(button));
        div.innerHTML = '<div class="container-fluid card-body bg-lobby"> <div class="row align-items-center white"><div class="col-6"><h3 class="card-title ml-3">' + roomname + '</h3></div><div class="col-6 text-right"><span class="card-text mr-3" style="white-space:nowrap;"><img src="images/players.png" class="mr-2" style="max-height: 30px; max-width: 30px;">'+ numplayer +'</span><span class="card-text mr-3"><span class="badge badge-danger">' + mode + '</span></span><br><button type="button" id="' + roomname + '" class="btn btn-success mr-2 mt-3" style="border-radius: 15px;" disabled>+ Unirse a esta sala</button></div></div></div>'
        card_list.appendChild(div);
        
        var button = document.getElementById(roomname);
        button.addEventListener("onClick", entrarEnSala(button.id));
    }
}


function entrarEnSala(roomName) {
    var message = {
        "protocolo": "entrar",
        "id": roomName;
    };
    game.socket.send(JSON.stringify(message));
}
