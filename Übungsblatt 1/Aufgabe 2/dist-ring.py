import argparse, random, select, socket, struct, time

# config
p0 = 0.5
k = 3

base_port = 10000
mcast_ip = '224.0.0.1'
mcast_port = 5007

def ring_node(node_id, node_ips):
    num_nodes = len(node_ips)
    p = p0

    token_socket = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    token_socket.bind(("0.0.0.0", base_port + node_id))

    mcast_recv_socket = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    mcast_recv_socket.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1) # erlaubt nutzung für mehrere Prozesse gelichzeitig
    
    mcast_recv_socket.bind(('', mcast_port))
    membership_request = struct.pack(
        '4s4s',
        socket.inet_aton(mcast_ip),
        socket.inet_aton('0.0.0.0')
    )
    mcast_recv_socket.setsockopt(socket.IPPROTO_IP, socket.IP_ADD_MEMBERSHIP, membership_request)

    mcast_send_socket = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    mcast_send_socket.setsockopt(socket.IPPROTO_IP, socket.IP_MULTICAST_TTL, 1) # Time to live 1 hält Nachrichten lokal
    mcast_send_socket.setsockopt(socket.IPPROTO_IP, socket.IP_MULTICAST_LOOP, 1) # Loopback, prozess erhält eigene Nachrichten

    next_node_id = (node_id + 1) % num_nodes
    next_node_address = (node_ips[next_node_id], base_port + next_node_id)

    terminated = False
    empty_rounds_count = 0

    # Statisitkerhebung für Teil 2 der Aufgabe
    total_rounds_count = 1
    round_times = []
    firework_count = 0
    last_token_time = time.time()

    if node_id == 0:
        token_socket.sendto(b'TOKEN', next_node_address)
        print(f"Node 0: Initiales Token an {next_node_address}")

    while not terminated:
        ready_sockets = select.select([token_socket, mcast_recv_socket], [], [])[0] # auf nachricht warten statt zu pollen
        for ready_socket in ready_sockets:
            if ready_socket is mcast_recv_socket:
                data, _ = mcast_recv_socket.recvfrom(100)
                if data == b'FIREWORK':
                    empty_rounds_count = 0
                    print(f'Round {total_rounds_count}: Firework heard')
            else:  # Token empfangen
                data = token_socket.recvfrom(100)
                print(
                    f'Round {total_rounds_count}: Token received.'
                )

                empty_rounds_count += 1
                total_rounds_count += 1
                now = time.time()
                round_times.append(now - last_token_time)
                last_token_time = now

                if empty_rounds_count == k:
                    terminated = True
                    print(f'Round {total_rounds_count}: This silence is deafening. Shutting down.')
                    print(f"Final: rounds={total_rounds_count}, multicasts={firework_count}, round_times={round_times}")

                if random.random() < p and terminated == False:
                    mcast_send_socket.sendto(
                        b'FIREWORK',
                        (mcast_ip, mcast_port)
                    )
                    print(f'Round {total_rounds_count}: FIREWORK')
                    firework_count += 1

                p /= 2
                
                time.sleep(0.1)
                token_socket.sendto(
                    f'TOKEN'.encode(),
                    next_node_address
                )

    time.sleep(1)
    token_socket.close()
    mcast_recv_socket.close()
    mcast_send_socket.close()

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "node_id",
        type=int,
        help="ID dieses Knotens"
    )
    parser.add_argument(
        "node_ips",
        nargs='+',
        help="IP-Adressen aller Knoten")
    args = parser.parse_args()

    ring_node(args.node_id, args.node_ips)