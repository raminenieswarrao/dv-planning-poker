"use strict";


(function () {

    /* =====================================================
       CREATE ANIMATION INSTANCE
       ===================================================== */

    function create(
        canvas
    ) {

        if (
            !canvas
            ||
            typeof canvas.getContext !==
            "function"
        ) {

            return null;
        }


        canvas.width =
            1280;


        canvas.height =
            720;


        const context =
            canvas.getContext(
                "2d"
            );


        const DURATION =
            10000;


        let winnerName =
            "WINNER";


        let loserName =
            "RUNNER-UP";


        let running =
            false;


        let animationFrameId =
            null;


        let startedAt =
            performance.now();


        /* =====================================================
           MATH HELPERS
           ===================================================== */

        const clamp =
            (
                number,
                minimum = 0,
                maximum = 1
            ) =>
                Math.max(
                    minimum,
                    Math.min(
                        maximum,
                        number
                    )
                );


        const progress =
            (
                time,
                start,
                end
            ) =>
                clamp(
                    (
                        time -
                        start
                    )
                    /
                    (
                        end -
                        start
                    )
                );


        const easeOut =
            value =>
                1
                -
                (
                    1 -
                    clamp(
                        value
                    )
                ) ** 3;


        const smooth =
            value => {

                value =
                    clamp(
                        value
                    );


                return value
                    *
                    value
                    *
                    (
                        3
                        -
                        2
                        *
                        value
                    );
            };


        const lerp =
            (
                start,
                end,
                amount
            ) =>
                start
                +
                (
                    end -
                    start
                )
                *
                amount;


        /* =====================================================
           TEXT
           ===================================================== */

        function drawText(
            text,
            x,
            y,
            size,
            color = "#ffffff",
            maxWidth
        ) {

            context.save();


            context.font =
                `800 ${size}px Arial`;


            context.textAlign =
                "center";


            context.textBaseline =
                "middle";


            context.fillStyle =
                color;


            if (
                Number.isFinite(
                    maxWidth
                )
            ) {

                context.fillText(
                    text,
                    x,
                    y,
                    maxWidth
                );

            } else {

                context.fillText(
                    text,
                    x,
                    y
                );
            }


            context.restore();
        }


        /* =====================================================
           ROUNDED RECTANGLE
           ===================================================== */

        function roundedRect(
            x,
            y,
            width,
            height,
            radius,
            fill,
            stroke
        ) {

            context.beginPath();


            context.roundRect(
                x,
                y,
                width,
                height,
                radius
            );


            context.fillStyle =
                fill;


            context.fill();


            if (stroke) {

                context.strokeStyle =
                    stroke;


                context.lineWidth =
                    2;


                context.stroke();
            }
        }


        /* =====================================================
           ARENA
           ===================================================== */

        function drawArena() {

            let gradient =
                context.createLinearGradient(
                    0,
                    0,
                    0,
                    720
                );


            gradient.addColorStop(
                0,
                "#061424"
            );


            gradient.addColorStop(
                0.5,
                "#123550"
            );


            gradient.addColorStop(
                1,
                "#020812"
            );


            context.fillStyle =
                gradient;


            context.fillRect(
                0,
                0,
                1280,
                720
            );


            /* =================================================
               STADIUM LIGHTS
               ================================================= */

            for (
                let index = 0;
                index < 6;
                index++
            ) {

                const lightX =
                    90
                    +
                    index
                    *
                    220;


                gradient =
                    context.createRadialGradient(
                        lightX,
                        65,
                        2,
                        lightX,
                        65,
                        92
                    );


                gradient.addColorStop(
                    0,
                    "rgba(91,220,255,.42)"
                );


                gradient.addColorStop(
                    1,
                    "rgba(91,220,255,0)"
                );


                context.fillStyle =
                    gradient;


                context.fillRect(
                    lightX - 95,
                    0,
                    190,
                    170
                );
            }


            drawText(
                "DV PICKLEBALL CHAMPIONSHIP",
                640,
                52,
                25,
                "#a8e8ff"
            );


            /* =================================================
               CROWD AREA
               ================================================= */

            context.fillStyle =
                "#081726";


            context.fillRect(
                0,
                115,
                1280,
                145
            );


            context.fillStyle =
                "#143a58";


            for (
                let row = 0;
                row < 4;
                row++
            ) {

                for (
                    let index = 0;
                    index < 31;
                    index++
                ) {

                    const crowdX =
                        10
                        +
                        index
                        *
                        43
                        +
                        (
                            row %
                            2
                        )
                        *
                        12;


                    context.beginPath();


                    context.arc(
                        crowdX,
                        143
                        +
                        row
                        *
                        27,
                        4,
                        0,
                        7
                    );


                    context.fill();
                }
            }


            /* =================================================
               COURT
               ================================================= */

            context.beginPath();


            context.moveTo(
                88,
                675
            );


            context.lineTo(
                1192,
                675
            );


            context.lineTo(
                985,
                245
            );


            context.lineTo(
                295,
                245
            );


            context.closePath();


            gradient =
                context.createLinearGradient(
                    0,
                    245,
                    0,
                    675
                );


            gradient.addColorStop(
                0,
                "#159186"
            );


            gradient.addColorStop(
                1,
                "#075661"
            );


            context.fillStyle =
                gradient;


            context.fill();


            context.strokeStyle =
                "#d8ffff";


            context.lineWidth =
                4;


            context.stroke();


            /* =================================================
               COURT LINES
               ================================================= */

            context.strokeStyle =
                "rgba(226,255,255,.8)";


            context.lineWidth =
                3;


            context.beginPath();


            context.moveTo(
                640,
                245
            );


            context.lineTo(
                640,
                675
            );


            context.moveTo(
                183,
                516
            );


            context.lineTo(
                1097,
                516
            );


            context.moveTo(
                253,
                338
            );


            context.lineTo(
                1027,
                338
            );


            context.stroke();
        }


        /* =====================================================
           NET
           ===================================================== */

        function drawNet() {

            context.save();


            context.fillStyle =
                "#e9ffff";


            context.fillRect(
                145,
                390,
                990,
                7
            );


            context.fillStyle =
                "rgba(3,12,22,.9)";


            context.fillRect(
                145,
                397,
                990,
                55
            );


            context.strokeStyle =
                "rgba(175,235,244,.38)";


            for (
                let x = 150;
                x < 1135;
                x += 14
            ) {

                context.beginPath();


                context.moveTo(
                    x,
                    398
                );


                context.lineTo(
                    x,
                    452
                );


                context.stroke();
            }


            for (
                let y = 406;
                y < 451;
                y += 10
            ) {

                context.beginPath();


                context.moveTo(
                    145,
                    y
                );


                context.lineTo(
                    1135,
                    y
                );


                context.stroke();
            }


            context.fillStyle =
                "#ffffff";


            context.fillRect(
                139,
                383,
                9,
                80
            );


            context.fillRect(
                1133,
                383,
                9,
                80
            );


            context.restore();
        }


        /* =====================================================
           BODY LIMB
           ===================================================== */

        function drawLimb(
            startX,
            startY,
            endX,
            endY,
            width,
            color
        ) {

            context.strokeStyle =
                color;


            context.lineWidth =
                width;


            context.lineCap =
                "round";


            context.lineJoin =
                "round";


            context.beginPath();


            context.moveTo(
                startX,
                startY
            );


            context.lineTo(
                endX,
                endY
            );


            context.stroke();
        }


        /* =====================================================
           ATHLETE
           ===================================================== */

        function drawAthlete(
            x,
            y,
            scale,
            direction,
            color,
            animation,
            fall = 0,
            winnerSide = true
        ) {

            context.save();


            context.translate(
                x,
                y
            );


            /* =================================================
               LOSER FALL
               ================================================= */

            if (fall) {

                context.rotate(
                    1.28
                    *
                    fall
                );


                context.translate(
                    15
                    *
                    fall,
                    -5
                    *
                    fall
                );
            }


            context.scale(
                direction
                *
                scale,
                scale
            );


            const run =
                animation.run
                ||
                0;


            const smash =
                animation.smash
                ||
                0;


            const win =
                animation.win
                ||
                0;


            const step =
                Math.sin(
                    run
                    *
                    Math.PI
                    *
                    8
                );


            const jump =
                Math.sin(
                    smash
                    *
                    Math.PI
                )
                *
                28;


            context.translate(
                0,
                -jump
            );


            /* =================================================
               SHADOW
               ================================================= */

            context.save();


            context.scale(
                direction,
                1
            );


            context.globalAlpha =
                0.28
                *
                (
                    1
                    -
                    fall
                );


            context.fillStyle =
                "#000000";


            context.beginPath();


            context.ellipse(
                0,
                13,
                42,
                9,
                0,
                0,
                7
            );


            context.fill();


            context.restore();


            const leg1 =
                step
                *
                25;


            const leg2 =
                -step
                *
                25;


            /* =================================================
               LEGS
               ================================================= */

            drawLimb(
                -9,
                -63,
                -18 + leg1,
                -25,
                17,
                "#111827"
            );


            drawLimb(
                -18 + leg1,
                -25,
                -36 + leg1,
                5,
                15,
                "#111827"
            );


            drawLimb(
                10,
                -63,
                22 + leg2,
                -25,
                17,
                "#172033"
            );


            drawLimb(
                22 + leg2,
                -25,
                43 + leg2,
                5,
                15,
                "#172033"
            );


            /* =================================================
               SHOES
               ================================================= */

            drawLimb(
                -40 + leg1,
                7,
                -15 + leg1,
                7,
                10,
                "#e6fbff"
            );


            drawLimb(
                39 + leg2,
                7,
                65 + leg2,
                7,
                10,
                "#e6fbff"
            );


            /* =================================================
               SHIRT
               ================================================= */

            const shirtGradient =
                context.createLinearGradient(
                    -30,
                    -150,
                    30,
                    -70
                );


            shirtGradient.addColorStop(
                0,
                color
            );


            shirtGradient.addColorStop(
                1,
                color ===
                "#ffb000"
                    ?
                    "#ef6c00"
                    :
                    "#b81042"
            );


            context.fillStyle =
                shirtGradient;


            context.beginPath();


            context.moveTo(
                -30,
                -146
            );


            context.quadraticCurveTo(
                0,
                -163,
                31,
                -145
            );


            context.lineTo(
                22,
                -65
            );


            context.quadraticCurveTo(
                0,
                -55,
                -21,
                -65
            );


            context.closePath();


            context.fill();


            /* =================================================
               HEAD
               ================================================= */

            context.fillStyle =
                "#b96f48";


            context.beginPath();


            context.arc(
                0,
                -183,
                27,
                0,
                7
            );


            context.fill();


            /* =================================================
               HAIR
               ================================================= */

            context.fillStyle =
                "#10151e";


            context.beginPath();


            context.arc(
                -3,
                -193,
                25,
                3.05,
                6.28
            );


            context.fill();


            /* =================================================
               FREE ARM / WINNER RAISE
               ================================================= */

            const raise =
                smooth(
                    win
                );


            drawLimb(
                -25,
                -132,
                -52
                +
                step
                *
                10,
                -102
                -
                raise
                *
                62,
                14,
                "#b96f48"
            );


            drawLimb(
                -52
                +
                step
                *
                10,
                -102
                -
                raise
                *
                62,
                -37,
                -72
                -
                raise
                *
                105,
                13,
                "#b96f48"
            );


            /* =================================================
               PADDLE ARM
               ================================================= */

            const swing =
                smooth(
                    progress(
                        smash,
                        0.12,
                        0.72
                    )
                );


            const angle =
                lerp(
                    -2.25,
                    0.35,
                    swing
                );


            context.save();


            context.translate(
                26,
                -132
            );


            context.rotate(
                angle
            );


            drawLimb(
                0,
                0,
                47,
                0,
                14,
                "#b96f48"
            );


            drawLimb(
                47,
                0,
                70,
                0,
                8,
                "#f5ffff"
            );


            context.fillStyle =
                winnerSide
                    ?
                    "#ffd129"
                    :
                    "#4ee5ff";


            context.strokeStyle =
                "#ffffff";


            context.lineWidth =
                3;


            context.beginPath();


            context.ellipse(
                91,
                0,
                24,
                34,
                0,
                0,
                7
            );


            context.fill();


            context.stroke();


            context.restore();


            context.restore();
        }


        /* =====================================================
           PLAYER NAME TAG
           ===================================================== */

        function drawNameTag(
            name,
            x,
            y,
            isWinner
        ) {

            context.save();


            context.shadowColor =
                isWinner
                    ?
                    "#ffd739"
                    :
                    "#5ee8ff";


            context.shadowBlur =
                isWinner
                    ?
                    18
                    :
                    9;


            roundedRect(
                x - 115,
                y - 20,
                230,
                40,
                20,

                isWinner
                    ?
                    "rgba(116,73,0,.92)"
                    :
                    "rgba(3,18,31,.9)",

                isWinner
                    ?
                    "#ffd739"
                    :
                    "#5ee8ff"
            );


            context.shadowBlur =
                0;


            drawText(
                name,
                x,
                y,
                18,

                isWinner
                    ?
                    "#fff3a0"
                    :
                    "#eaffff",

                205
            );


            context.restore();
        }


        /* =====================================================
           PICKLEBALL
           ===================================================== */

        function drawBall(
            x,
            y,
            animationProgress
        ) {

            context.save();


            for (
                let index = 8;
                index > 0;
                index--
            ) {

                context.globalAlpha =
                    0.045
                    *
                    index;


                context.fillStyle =
                    index %
                    2
                        ?
                        "#ff3b00"
                        :
                        "#ffd500";


                context.beginPath();


                context.ellipse(
                    x
                    -
                    index
                    *
                    20
                    *
                    animationProgress,

                    y
                    +
                    index
                    *
                    5
                    *
                    animationProgress,

                    12
                    +
                    index
                    *
                    4,

                    8
                    +
                    index
                    *
                    2,

                    0,
                    0,
                    7
                );


                context.fill();
            }


            context.globalAlpha =
                1;


            context.shadowColor =
                "#deff20";


            context.shadowBlur =
                30;


            context.fillStyle =
                "#dcff32";


            context.beginPath();


            context.arc(
                x,
                y,
                14,
                0,
                7
            );


            context.fill();


            context.restore();
        }


        /* =====================================================
           POWER IMPACT
           ===================================================== */

        function drawImpactBurst(
            x,
            y,
            time
        ) {

            if (
                time <= 0
                ||
                time >= 1
            ) {

                return;
            }


            context.save();


            context.globalAlpha =
                1
                -
                time;


            context.translate(
                x,
                y
            );


            for (
                let index = 0;
                index < 22;
                index++
            ) {

                const angle =
                    index
                    *
                    Math.PI
                    *
                    2
                    /
                    22;


                const radius =
                    30
                    +
                    time
                    *
                    145;


                context.strokeStyle =
                    index %
                    2
                        ?
                        "#fff14a"
                        :
                        "#ff3900";


                context.lineWidth =
                    7
                    *
                    (
                        1
                        -
                        time
                    )
                    +
                    1;


                context.beginPath();


                context.moveTo(
                    Math.cos(
                        angle
                    )
                    *
                    18,

                    Math.sin(
                        angle
                    )
                    *
                    18
                );


                context.lineTo(
                    Math.cos(
                        angle
                    )
                    *
                    radius,

                    Math.sin(
                        angle
                    )
                    *
                    radius
                );


                context.stroke();
            }


            context.restore();
        }


        /* =====================================================
           WINNER BANNER
           ===================================================== */

        function drawWinnerBanner(
            time
        ) {

            if (!time) {

                return;
            }


            const bannerProgress =
                easeOut(
                    time
                );


            const y =
                lerp(
                    -95,
                    99,
                    bannerProgress
                );


            context.save();


            context.shadowColor =
                "#ffc928";


            context.shadowBlur =
                35;


            roundedRect(
                330,
                y - 48,
                620,
                96,
                22,
                "rgba(4,15,29,.96)",
                "#ffd542"
            );


            context.shadowBlur =
                0;


            drawText(
                "🏆  WINNER — "
                +
                winnerName
                +
                "  🏆",
                640,
                y,
                35,
                "#fff4ac",
                565
            );


            context.restore();
        }


        /* =====================================================
           MAIN FRAME
           ===================================================== */

        function drawFrame(
            timestamp
        ) {

            if (!running) {

                return;
            }


            const time =
                (
                    timestamp
                    -
                    startedAt
                )
                %
                DURATION;


            drawArena();


            const run =
                progress(
                    time,
                    500,
                    2850
                );


            const smash =
                progress(
                    time,
                    2500,
                    4100
                );


            const flight =
                progress(
                    time,
                    3750,
                    5100
                );


            const impact =
                progress(
                    time,
                    5000,
                    5520
                );


            const fall =
                progress(
                    time,
                    5050,
                    6500
                );


            const win =
                progress(
                    time,
                    6250,
                    7350
                );


            const banner =
                progress(
                    time,
                    7150,
                    7900
                );


            /* =================================================
               LOSER
               ================================================= */

            drawAthlete(
                820,
                373,
                0.76,
                -1,
                "#ef3362",

                {
                    run:
                        0,

                    smash:
                        0.08
                },

                fall,

                false
            );


            drawNameTag(
                loserName,
                820,
                348,
                false
            );


            /* =================================================
               BALL FLIGHT
               ================================================= */

            if (
                time > 3550
                &&
                time < 5350
            ) {

                const ballX =
                    lerp(
                        500,
                        805,
                        easeOut(
                            flight
                        )
                    );


                const ballY =
                    lerp(
                        450,
                        315,
                        flight
                    )
                    -
                    Math.sin(
                        flight
                        *
                        Math.PI
                    )
                    *
                    105;


                drawBall(
                    ballX,
                    ballY,
                    0.6
                    +
                    flight
                );
            }


            /* =================================================
               IMPACT
               ================================================= */

            drawImpactBurst(
                805,
                318,
                impact
            );


            /* =================================================
               NET
               ================================================= */

            drawNet();


            /* =================================================
               WINNER
               ================================================= */

            const winnerX =
                lerp(
                    205,
                    475,
                    easeOut(
                        run
                    )
                );


            const winnerY =
                lerp(
                    610,
                    582,
                    easeOut(
                        run
                    )
                );


            drawAthlete(
                winnerX,
                winnerY,
                1.08,
                1,
                "#ffb000",

                {
                    run,
                    smash,
                    win
                },

                0,

                true
            );


            drawNameTag(
                winnerName,
                winnerX,
                630,
                true
            );


            /* =================================================
               RUN SPEED LINES
               ================================================= */

            if (
                run > 0.05
                &&
                run < 0.95
            ) {

                context.save();


                context.globalAlpha =
                    0.5
                    *
                    (
                        1
                        -
                        run
                    );


                context.strokeStyle =
                    "#72ebff";


                context.lineWidth =
                    5;


                for (
                    let index = 0;
                    index < 6;
                    index++
                ) {

                    context.beginPath();


                    context.moveTo(
                        winnerX
                        -
                        170
                        -
                        index
                        *
                        15,

                        470
                        +
                        index
                        *
                        22
                    );


                    context.lineTo(
                        winnerX
                        -
                        65,

                        470
                        +
                        index
                        *
                        22
                    );


                    context.stroke();
                }


                context.restore();
            }


            /* =================================================
               MATCH POINT
               ================================================= */

            if (
                time < 500
            ) {

                context.globalAlpha =
                    1
                    -
                    time
                    /
                    500;


                drawText(
                    "MATCH POINT",
                    640,
                    292,
                    43
                );


                context.globalAlpha =
                    1;
            }


            /* =================================================
               WINNER BANNER
               ================================================= */

            drawWinnerBanner(
                banner
            );


            animationFrameId =
                requestAnimationFrame(
                    drawFrame
                );
        }


        /* =====================================================
           NAME CONTROL
           ===================================================== */

        function normalizeName(
            value,
            fallback
        ) {

            if (
                typeof value !==
                "string"
                ||
                !value.trim()
            ) {

                return fallback;
            }


            return value
                .trim()
                .replace(
                    /\s+/g,
                    " "
                )
                .toUpperCase();
        }


        function setNames(
            newWinnerName,
            newLoserName
        ) {

            winnerName =
                normalizeName(
                    newWinnerName,
                    "WINNER"
                );


            loserName =
                normalizeName(
                    newLoserName,
                    "RUNNER-UP"
                );
        }


        /* =====================================================
           START
           ===================================================== */

        function start() {

            if (running) {

                return;
            }


            running =
                true;


            startedAt =
                performance.now();


            animationFrameId =
                requestAnimationFrame(
                    drawFrame
                );
        }


        /* =====================================================
           STOP
           ===================================================== */

        function stop() {

            running =
                false;


            if (
                animationFrameId !==
                null
            ) {

                cancelAnimationFrame(
                    animationFrameId
                );


                animationFrameId =
                    null;
            }
        }


        /* =====================================================
           DESTROY
           ===================================================== */

        function destroy() {

            stop();


            context.clearRect(
                0,
                0,
                canvas.width,
                canvas.height
            );
        }


        /* =====================================================
           PUBLIC API
           ===================================================== */

        return {

            setNames,

            start,

            stop,

            destroy
        };
    }


    /* =====================================================
       GLOBAL EXPORT
       ===================================================== */

    window.DVPickleballVictory = {

        create
    };

})();