(function() {
    let ws = null;
    let currentRevision = 1;
    let currentSnapshot = null;

    const urlParams = new URLSearchParams(window.location.search);
    const pairingToken = urlParams.get("pair") || "";
    const roomId = urlParams.get("room") || "";

    const storedRoomId = localStorage.getItem("poker_room_id");
    if (roomId && roomId !== storedRoomId) {
        localStorage.removeItem("poker_seat_token");
        localStorage.removeItem("poker_player_id");
        localStorage.setItem("poker_room_id", roomId);
    }

    let seatToken = localStorage.getItem("poker_seat_token");
    let myPlayerId = localStorage.getItem("poker_player_id");

    const badge = document.getElementById("connection-badge");
    const lobbyModal = document.getElementById("lobby-modal");
    const seatsList = document.getElementById("seats-list");
    const roomInfo = document.getElementById("room-info");
    const potLabel = document.getElementById("pot-amount");
    const streetLabel = document.getElementById("street-label");
    const liveBetLabel = document.getElementById("live-bet-amount");
    const boardCardsEl = document.getElementById("board-cards");
    const playersListEl = document.getElementById("players-list");
    const waitingMsg = document.getElementById("waiting-msg");
    const activeControls = document.getElementById("active-controls");
    const raiseBox = document.getElementById("raise-controls-box");
    const targetInput = document.getElementById("target-amount-input");

    const foldBtn = document.getElementById("fold-btn");
    const checkBtn = document.getElementById("check-btn");
    const callBtn = document.getElementById("call-btn");
    const betBtn = document.getElementById("bet-btn");
    const raiseBtn = document.getElementById("raise-btn");
    const allinBtn = document.getElementById("allin-btn");

    function connect() {
        const protocol = window.location.protocol === "https:" ? "wss:" : "ws:";
        const wsUrl = `${protocol}//${window.location.host}/ws/poker`;

        badge.textContent = "Connecting...";
        badge.className = "badge badge-connecting";

        ws = new WebSocket(wsUrl);

        ws.onopen = function() {
            badge.textContent = "🟢 Connected";
            badge.className = "badge badge-connected";

            if (seatToken && myPlayerId) {
                sendMsg({
                    actionId: "reconnect-" + Date.now(),
                    seatToken: seatToken,
                    payload: { type: "reconnect", lastSeenRevision: currentRevision }
                });
            } else {
                loadLobbySeats();
            }
        };

        ws.onmessage = function(event) {
            const msg = JSON.parse(event.data);
            handleServerMessage(msg);
        };

        ws.onclose = function() {
            badge.textContent = "🟡 Reconnecting...";
            badge.className = "badge badge-connecting";
            setTimeout(connect, 2000);
        };
    }

    function sendMsg(obj) {
        if (ws && ws.readyState === WebSocket.OPEN) {
            ws.send(JSON.stringify(obj));
        }
    }

    async function loadLobbySeats() {
        try {
            const res = await fetch("/lobby");
            const data = await res.json();
            roomInfo.textContent = `Room: ${data.roomId} (${data.sessionName})`;
            seatsList.innerHTML = "";

            data.availableSeats.forEach(seat => {
                const btn = document.createElement("button");
                btn.className = "seat-btn";
                btn.textContent = seat.displayName + (seat.isOccupied ? " (Taken)" : " (Claim)");
                btn.disabled = seat.isOccupied;

                btn.onclick = () => {
                    sendMsg({
                        actionId: "claim-" + Date.now(),
                        payload: {
                            type: "claim_seat",
                            requestedPlayerId: seat.playerId,
                            pairingToken: pairingToken
                        }
                    });
                };
                seatsList.appendChild(btn);
            });
            lobbyModal.classList.remove("hidden");
        } catch (_) {
            setTimeout(loadLobbySeats, 3000);
        }
    }

    function handleServerMessage(msg) {
        currentRevision = msg.stateRevision || currentRevision;

        const payload = msg.payload;
        if (!payload) return;

        if (payload.type === "seat_confirmed") {
            seatToken = payload.seatToken;
            myPlayerId = payload.playerId;
            localStorage.setItem("poker_seat_token", seatToken);
            localStorage.setItem("poker_player_id", myPlayerId);
            lobbyModal.classList.add("hidden");
        } else if (payload.type === "game_snapshot") {
            lobbyModal.classList.add("hidden");
            renderSnapshot(payload.snapshot);
        } else if (payload.type === "action_ack") {
            // Action acknowledged
        } else if (payload.type === "action_nack" || payload.type === "error") {
            const reason = msg.errorMessage || payload.reason || "Action rejected";
            if (reason.toLowerCase().includes("token")) {
                localStorage.removeItem("poker_seat_token");
                localStorage.removeItem("poker_player_id");
                seatToken = null;
                myPlayerId = null;
                loadLobbySeats();
            } else {
                alert(reason);
            }
        }
    }

    function renderSnapshot(snap) {
        currentSnapshot = snap;
        const table = snap.table;
        const privateState = snap.privateState;

        potLabel.textContent = `Pot: ${table.totalPotChips} chips`;
        streetLabel.textContent = table.street;
        liveBetLabel.textContent = table.currentLiveBet;

        boardCardsEl.innerHTML = "";
        if (!table.communityCards || table.communityCards.length === 0) {
            boardCardsEl.innerHTML = `<div class="empty-board-text">No community cards yet</div>`;
        } else {
            table.communityCards.forEach(c => boardCardsEl.appendChild(createCardEl(c)));
        }

        playersListEl.innerHTML = "";
        table.players.forEach(p => {
            const row = document.createElement("div");
            row.className = `player-card ${p.isTurn ? "active-turn" : ""} ${p.isFolded ? "folded" : ""}`;
            const isMe = p.playerId === myPlayerId;
            row.innerHTML = `
                <div>
                    <b>${p.displayName}</b> ${isMe ? "(You)" : ""} ${p.isDealer ? "<span class='text-gold'>(DEALER)</span>" : ""}
                    <div style="font-size:11px; color:#B0BEC5;">Stack: ${p.remainingStack}</div>
                </div>
                <div style="text-align:right;">
                    <span class="text-gold font-bold">Bet: ${p.streetContribution}</span>
                    <div style="font-size:11px;">${p.isFolded ? "<span style='color:#EF5350'>FOLDED</span>" : (p.isAllIn ? "<span style='color:#4FC3F7'>ALL-IN</span>" : "")}</div>
                </div>
            `;
            playersListEl.appendChild(row);
        });

        if (privateState.isYourTurn && privateState.legalActions) {
            waitingMsg.classList.add("hidden");
            activeControls.classList.remove("hidden");
            renderActionButtons(privateState.legalActions);
            if (navigator.vibrate) navigator.vibrate([100, 50, 100]);
        } else {
            waitingMsg.classList.remove("hidden");
            activeControls.classList.add("hidden");
        }
    }

    function createCardEl(card) {
        const div = document.createElement("div");
        const isRed = card.suit === 'h' || card.suit === 'd';
        const suitSymbol = card.suit === 's' ? '♠' : (card.suit === 'h' ? '♥' : (card.suit === 'd' ? '♦' : '♣'));
        div.className = `card ${isRed ? "red" : "black"}`;
        div.textContent = `${card.rank}${suitSymbol}`;
        return div;
    }

    function renderActionButtons(legal) {
        foldBtn.classList.toggle("hidden", !legal.canFold);
        checkBtn.classList.toggle("hidden", !legal.canCheck);

        callBtn.classList.toggle("hidden", !legal.canCall);
        if (legal.canCall) callBtn.textContent = `Call ${legal.callAmount}`;

        betBtn.classList.toggle("hidden", !legal.canBet);
        raiseBtn.classList.toggle("hidden", !legal.canRaise);
        allinBtn.classList.toggle("hidden", !legal.canAllIn);

        const showRaiseBox = legal.canBet || legal.canRaise;
        raiseBox.classList.toggle("hidden", !showRaiseBox);

        if (showRaiseBox) {
            const min = legal.canBet ? legal.minBetAmount : legal.minRaiseTo;
            const max = legal.canBet ? legal.maxBetAmount : legal.maxRaiseTo;
            targetInput.min = min;
            targetInput.max = max;
            targetInput.value = min;

            document.getElementById("target-label").textContent = legal.canBet ? "Bet Amount:" : "Raise To:";
            document.getElementById("min-btn").onclick = () => targetInput.value = min;
            document.getElementById("plus5-btn").onclick = () => targetInput.value = Math.min(max, parseInt(targetInput.value || min) + 5);
            document.getElementById("plus10-btn").onclick = () => targetInput.value = Math.min(max, parseInt(targetInput.value || min) + 10);
            document.getElementById("pot-btn").onclick = () => {
                const totalPot = currentSnapshot ? currentSnapshot.table.totalPotChips : min;
                targetInput.value = Math.min(max, Math.max(min, totalPot));
            };
        }
    }

    function submitAction(type, amount = 0) {
        sendMsg({
            actionId: "act-" + Date.now(),
            seatToken: seatToken,
            payload: {
                type: "request_action",
                actionType: type,
                targetAmount: parseInt(amount) || 0
            }
        });
    }

    foldBtn.onclick = () => submitAction("FOLD");
    checkBtn.onclick = () => submitAction("CHECK");
    callBtn.onclick = () => submitAction("CALL");
    betBtn.onclick = () => submitAction("BET", targetInput.value);
    raiseBtn.onclick = () => submitAction("RAISE", targetInput.value);
    allinBtn.onclick = () => submitAction("ALL_IN");

    document.addEventListener("visibilitychange", function() {
        if (document.visibilityState === "visible") {
            if (!ws || ws.readyState !== WebSocket.OPEN) connect();
        }
    });

    connect();
})();
