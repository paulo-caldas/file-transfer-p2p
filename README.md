# Notas

- Enviar periodicamente ping em multicast... e aguardar resposta.
  - Se um conhecido peer responder com PONG, marcar-lo como vivo
  - Se um desconhecido peer responder com HELLO, adicionar-lo como conhecido

- PeerKeepaliveDaemon.java
```Java
while (running) {

    sendPing(Multicast);
    Thread.sleep(SLEEP_TIME);
    removeDeadPeers();
}
```

- ListeningDaemon.java
```Java
    PDU pdu = sock.receive();

    if (pdu.isPing()) {

        if(knownPeer(pdu.getSrc())) {
            sendPong();
        } else {
            sendHello();
        }

    }
```
- PONGs retornam também meta-dados informativos p.e. a versão da tua tabela de routing : assim sei se estou desatualizado ou não
- O hash da tabela valerá a pena? Ou basta incrementar um inteiro sempre que a tabela muda... Bastará para aferir frescura
