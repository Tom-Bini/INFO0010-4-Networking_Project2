# NOTES FROM SLIDES

parse url example : 

Java.net.URL

u = new URL(<string>)
u.getPort()

# ===============================================

encode the logo image :

3 bytes (24 bits) become 4 6-bits characters

ex: <img src="data:image/png;base64,iVBORw..." />

# ===============================================

- "Use Websocket.java file as interface on the server"

- Implement thread pool to prevent ddos attacks

- Use chunked encoding to send through tcp

# ===============================================

### NOTES FROM PDF

Deadline : 11/12/2027

Game state server side as before

Not just 1 tcp connection for the whole session but 1 for every request, indexed by session cookie

# HTTP GET method :
  - Request of the page or assets
  - open websocket (/ws)

# HTTP POST method :
  - update username -> redirect (See Other)

Do not have to write corresponding client code, browser is the client

Launch command :

java BlackjackServer {maxThreads}

server listens on port 8014

# Compulsory elements on play.html :
  - logo.png as base64 (6 bits) encoded image
  - <form> text field to update the username with POST request
  - <form> to place a bet
  - buttons to hit or stand
  - table displaying :
    - dealer's hand (<img>)
    - dealer's current total
    - player's hand (<img>)
    - player's current total
    - player's bankroll

Call to ("/") redirects to play.html

Verifying validity of client's requests

Verify if javascript is enabled on client's browser, and if not prevent that the game cannot be played without it

# BP v2 :
  All same than the v1 but a new commant : "STATUS", returning hands if in round and "NOT IN ROUND" otherwise.

Chunk max size : 128 bytes -> 1024 bits -> 170,6666 x 6 bits -> 170 6-bits encoded caracters

Chunks used to transfer play.html content

# User auth :
  - handled with session cookies
  - new user -> generate random value for session ID -> transfered to the client -> each connexion request from client, he provides it to access its session
  - Cookie lifetime : 10 min

# Compulsory http answer code to handle :
  101, 200, 303, 400, 404, 405, 411, 501 and 505

# Going further :
  - any additional work networking-related can improve your marks
  - exemples : reducing network usage or exploiting the bi-directional nature of the websocket connection
  - if implemented, will have to present and justified it in the oral exam

# Report :
  - Max 5 pages
  - following the provided template
  - parts :
    - Software architecture
    - Multi-thread coordination
    - Limits and Improvements
    - Tests
  
# Guidelines

  - Java 17
  - Following packages : java.lang, java.io, java.net, java.awt, javax.imageio, javax.security, java.util (but if we would like to use another one we can ask)
  - Handling shutting down with ctrl+c
  - avoid using ShutdownHooks
  - not manipulating files apart reading cards visuals
  - NOT INCLUDING the websocket.java in the zip file

# HTTP Request Overall Syntax
<http-request> ::= <method-name> <url> HTTP/<http-version> CRLF
<http-headers> CRLF
CRLF


# Overall HTTP reply syntax
<http-response> ::= HTTP/<http-version> <ret-code> <status> CRLF
<http-headers> CRLF
CRLF
[response-body]


# Ways to redirect :
  - Return a 303 See Other with a Location field
  - Return a 200 with a script asking for a new request

# Javascript functions presented :
  EventListeners
  document.getElementById()
  <NOSCRIPT> tag will be read if the client doesn't have javascript

# Websocket :

  Initiating handshake :

  Connection: Upgrade
  Upgrade: websocket
  Sec-WebSocket-Version: 13
  Sec-WebSocket-Key: client_key

  Server's Response :

  HTTP/1.1 101 Switching Protocols
  Connection: Upgrade
  Upgrade: websocket
  Sec-WebSocket-Accept: server_key

# Image encoding :

  java :

  final ByteArrayOutputStream os = new ByteArrayOutputStream();
  ImageIO.write(image, "png", os);
  String s = Base64.getEncoder().encodeToString(os.toByteArray());

  then in html :

  <img src="data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAZAAA..." />

# cookie fields :

  Creating one :

  Set-Cookie: SESSID=rk64vvmhlbt6rsdfv4f02kc5g0; path=/

  Deleting one :

  Set-Cookie: SESSID=deleted; path=/; expires=Thu, 01 Jan 1970 00:00:00
GMT

# Chunked encoding

  Transfer-Encoding: chunked

  transfer is finished when a block of size 0 is sent (followed by 2 crlf)

# Thread pool

  java.util.concurrent.Executors

  calling 

  newFixedThreadPool(int maxThreads)

  execute(Runnable worker)