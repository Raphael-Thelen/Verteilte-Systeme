Usage:

Das Programm ring.py kann einzeln ausgeführt werden und nimmt dann n=5 als Standart an. Das Programm test.py erzeugt die folgenden Testergebnisse:

--- Summary ---
Nodes |  Status  |   Rounds | Multicasts |      Min |      Avg |      Max | Duration
--------------------------------------------------------------------------------
    2 | Success  |       15 |          3 |   0.0001 |   0.1476 |   0.9584 |     1.10s
    4 | Success  |       22 |          3 |   0.0002 |   0.2129 |   0.9580 |     1.10s
    8 | Success  |       66 |          7 |   0.0003 |   0.1305 |   0.9468 |     1.11s
   16 | Success  |       81 |          7 |   0.0006 |   0.2264 |   0.9362 |     1.15s
   32 | Success  |      331 |         29 |   0.0020 |   0.1067 |   1.1242 |     1.48s
   64 | Success  |      506 |         57 |   0.0016 |   0.1550 |   1.5345 |     1.86s
  128 | Success  |     1415 |        135 |   0.0031 |   0.1601 |   2.3463 |     2.95s
  256 | Success  |     2980 |        251 |   0.0112 |   0.3135 |   4.0839 |     5.63s
  512 | Success  |     6570 |        487 |   0.0166 |   0.6685 |   7.9238 |    12.34s
 1024 | Success  |    13018 |        999 |   0.1723 |   2.6357 |  15.9210 |    39.97s

Maximales erfolgreiches n: 1024

Die Höhe der erreichbaren n hängt von der zulässigen Menge an parallelen Prozessen im Betriebssystem ab.
Ein Test mit 2048 Nodes hat zum einfrieren des Systems geführt.

Getestet auf MacOS.

