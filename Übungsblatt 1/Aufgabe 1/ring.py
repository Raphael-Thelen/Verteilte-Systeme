import argparse, multiprocessing, random, select, socket, struct, time

# config
num_nodes_default = 5
p0 = 0.5
k = 3

base_port = 10000
mcast_ip = '224.0.0.1'
mcast_port = 5007
local_ip = '127.0.0.1'

def ring_node(node_id, num_nodes):
    p = p0

    token_socket = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    token_socket.bind((local_ip, base_port + node_id))

    mcast_recv_socket = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    mcast_recv_socket.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1) # erlaubt nutzung für mehrere Prozesse gelichzeitig
    mcast_recv_socket.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEPORT, 1)
    mcast_recv_socket.bind(('', mcast_port))
    membership_request = struct.pack(
        '4s4s',
        socket.inet_aton(mcast_ip),
        socket.inet_aton(local_ip)
    )
    mcast_recv_socket.setsockopt(socket.IPPROTO_IP, socket.IP_ADD_MEMBERSHIP, membership_request)

    mcast_send_socket = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    mcast_send_socket.setsockopt(socket.IPPROTO_IP, socket.IP_MULTICAST_TTL, 1) # Time to live 1 hält Nachrichten lokal
    mcast_send_socket.setsockopt(socket.IPPROTO_IP, socket.IP_MULTICAST_LOOP, 1) # Loopback, prozess erhält eigene Nachrichten
    mcast_send_socket.setsockopt(socket.IPPROTO_IP, socket.IP_MULTICAST_IF, socket.inet_aton(local_ip))

    next_node_address = (
        local_ip,
        base_port + (node_id + 1) % num_nodes
    )

    terminated = False
    empty_rounds_count = 0

    # Statisitkerhebung für Teil 2 der Aufgabe
    total_rounds_count = 1
    round_times = []
    firework_count = 0
    last_token_time = time.time()

    while not terminated:
        ready_sockets = select.select([token_socket, mcast_recv_socket], [], [])[0] # auf nachricht warten statt zu pollen
        for ready_socket in ready_sockets:
            if ready_socket is mcast_recv_socket:
                data, _ = mcast_recv_socket.recvfrom(100)
                if data == b'FIREWORK':
                    empty_rounds_count = 0
            else:  # Token empfangen
                data = token_socket.recvfrom(100)
                print(
                    f'Node {node_id}: Token received.'
                )

                empty_rounds_count += 1

                total_rounds_count += 1
                now = time.time()
                round_times.append(now - last_token_time)
                last_token_time = now

                if empty_rounds_count == k:
                    terminated = True
                    print(f'Node {node_id}: This silence is deafening. Shutting down.')
                    print(f"Node {node_id} final: rounds={total_rounds_count}, multicasts={firework_count}, round_times={round_times}")

                if random.random() < p and terminated == False:
                    mcast_send_socket.sendto(
                        b'FIREWORK',
                        (mcast_ip, mcast_port)
                    )
                    print(f'Node {node_id}: FIREWORK')
                    firework_count += 1

                p /= 2
                token_socket.sendto(
                    f'TOKEN'.encode(),
                    next_node_address
                )

    token_socket.close()
    mcast_recv_socket.close()
    mcast_send_socket.close()

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "num_nodes",
        nargs="?",
        type=int,
        default=num_nodes_default,
        help="Anzahl der Nodes im Ring (Standard: 5)"
    )
    args = parser.parse_args()
    num_nodes = args.num_nodes

    processes = [
        multiprocessing.Process(target=ring_node, args=(i, num_nodes))
        for i in range(num_nodes)
    ]
    for process in processes:
        process.start()
    time.sleep(1)
    initial_token_socket = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    initial_token_socket.sendto(
        b'TOKEN', (local_ip, base_port)
    )
    initial_token_socket.close()

    for process in processes:
        process.join()
