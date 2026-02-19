const pptxgen = require('pptxgenjs');
const path = require('path');

async function createPresentation() {
    const pptx = new pptxgen();
    pptx.layout = 'LAYOUT_16x9';
    pptx.author = 'PocketServer Team';
    pptx.title = 'PocketServer - 서랍 속 스마트폰이 나만의 서버가 됩니다';

    const RED = 'EB0028';
    const WHITE = 'FFFFFF';
    const BLACK = '000000';
    const GRAY = '999999';
    const DARK_GRAY = '1A1A1A';

    // Slide 1: Title
    let s1 = pptx.addSlide();
    s1.background = { color: BLACK };
    s1.addText([
        { text: '서랍 속 스마트폰이\n', options: { color: WHITE, fontSize: 42, bold: true } },
        { text: '나만의 서버', options: { color: RED, fontSize: 42, bold: true } },
        { text: '가 됩니다', options: { color: WHITE, fontSize: 42, bold: true } }
    ], { x: 0.8, y: 1.2, w: 8.4, h: 2.5, align: 'center', lineSpacingMultiple: 1.3 });
    s1.addText('PocketServer — 구형 스마트폰을 리눅스 서버로 변환하는 원클릭 앱', {
        x: 1.5, y: 3.8, w: 7, h: 0.6, color: '888888', fontSize: 16, align: 'center'
    });

    // Slide 2: Problem
    let s2 = pptx.addSlide();
    s2.background = { color: BLACK };
    s2.addText('수억 대', {
        x: 0, y: 0.8, w: 10, h: 1.5, color: RED, fontSize: 72, bold: true, align: 'center'
    });
    s2.addText('전 세계 구형 스마트폰이\n서랍 속에 잠들어 있습니다', {
        x: 1, y: 2.4, w: 8, h: 1.2, color: WHITE, fontSize: 24, align: 'center', lineSpacingMultiple: 1.4
    });
    s2.addText('ARM 4-8코어 · 3-4GB RAM · WiFi · 소비전력 5W 미만', {
        x: 1, y: 4.0, w: 8, h: 0.5, color: '666666', fontSize: 14, align: 'center'
    });

    // Slide 3: Insight
    let s3 = pptx.addSlide();
    s3.background = { color: BLACK };
    s3.addText([
        { text: '그 스마트폰은\n', options: { color: WHITE, fontSize: 36, bold: true } },
        { text: '충분히 강력합니다', options: { color: RED, fontSize: 48, bold: true } }
    ], { x: 1, y: 1.0, w: 8, h: 2.5, align: 'center', lineSpacingMultiple: 1.4 });
    s3.addText('AI 에이전트, 웹서버, 자동화 도구를\n24시간 돌릴 수 있는 성능', {
        x: 1, y: 3.5, w: 8, h: 1, color: GRAY, fontSize: 18, align: 'center', lineSpacingMultiple: 1.4
    });

    // Slide 4: Solution
    let s4 = pptx.addSlide();
    s4.background = { color: BLACK };
    s4.addText('SOLUTION', {
        x: 0, y: 1.2, w: 10, h: 0.5, color: RED, fontSize: 16, align: 'center', charSpacing: 4
    });
    s4.addText('PocketServer', {
        x: 0, y: 1.8, w: 10, h: 1.2, color: WHITE, fontSize: 48, bold: true, align: 'center'
    });
    s4.addText('버튼 하나로 구형 스마트폰을\nUbuntu 리눅스 서버로 변환', {
        x: 1.5, y: 3.2, w: 7, h: 1, color: 'CCCCCC', fontSize: 20, align: 'center', lineSpacingMultiple: 1.5
    });

    // Slide 5: Architecture
    let s5 = pptx.addSlide();
    s5.background = { color: BLACK };
    s5.addText([
        { text: '2-App ', options: { color: WHITE, fontSize: 28, bold: true } },
        { text: 'Architecture', options: { color: RED, fontSize: 28, bold: true } }
    ], { x: 0, y: 0.3, w: 10, h: 0.8, align: 'center' });

    // Monitor Card
    s5.addShape(pptx.shapes.ROUNDED_RECTANGLE, {
        x: 0.8, y: 1.3, w: 4, h: 3.5, fill: { color: DARK_GRAY }, rectRadius: 0.1
    });
    s5.addShape(pptx.shapes.RECTANGLE, {
        x: 0.8, y: 1.3, w: 4, h: 0.06, fill: { color: '3B82F6' }
    });
    s5.addText('PocketMonitor', {
        x: 1.0, y: 1.5, w: 3.6, h: 0.5, color: WHITE, fontSize: 20, bold: true
    });
    s5.addText('Google Play Store\n\n디바이스 헬스 모니터링\n서버 상태 대시보드\n일일 안전 리포트\nAdMob 광고 수익화', {
        x: 1.0, y: 2.1, w: 3.6, h: 2.0, color: GRAY, fontSize: 12, lineSpacingMultiple: 1.5
    });
    s5.addText('Play Store 정책 100% 준수', {
        x: 1.0, y: 4.2, w: 3.6, h: 0.4, color: '3B82F6', fontSize: 10
    });

    // Engine Card
    s5.addShape(pptx.shapes.ROUNDED_RECTANGLE, {
        x: 5.2, y: 1.3, w: 4, h: 3.5, fill: { color: DARK_GRAY }, rectRadius: 0.1
    });
    s5.addShape(pptx.shapes.RECTANGLE, {
        x: 5.2, y: 1.3, w: 4, h: 0.06, fill: { color: RED }
    });
    s5.addText('PocketEngine', {
        x: 5.4, y: 1.5, w: 3.6, h: 0.5, color: WHITE, fontSize: 20, bold: true
    });
    s5.addText('사이드로드 (Firebase Hosting)\n\n원클릭 Ubuntu 24.04 설치\nDropbear SSH 서버\n7계층 Keep-Alive\n백그라운드 상시 가동', {
        x: 5.4, y: 2.1, w: 3.6, h: 2.0, color: GRAY, fontSize: 12, lineSpacingMultiple: 1.5
    });
    s5.addText('광고 없음 · 완전 무료', {
        x: 5.4, y: 4.2, w: 3.6, h: 0.4, color: RED, fontSize: 10
    });

    // Slide 6: Monitor screenshot
    let s6 = pptx.addSlide();
    s6.background = { color: BLACK };
    s6.addText('MONITORING', {
        x: 0.6, y: 0.8, w: 4.5, h: 0.4, color: RED, fontSize: 14, charSpacing: 3
    });
    s6.addText('디바이스 건강을\n한눈에 파악', {
        x: 0.6, y: 1.3, w: 4.5, h: 1.5, color: WHITE, fontSize: 32, bold: true, lineSpacingMultiple: 1.3
    });
    s6.addText('CPU, 메모리, 온도, 저장공간\n실시간 모니터링\n건강 점수 0-100 알고리즘', {
        x: 0.6, y: 3.0, w: 4.5, h: 1.2, color: GRAY, fontSize: 14, lineSpacingMultiple: 1.6
    });
    s6.addImage({
        path: path.resolve(__dirname, 'playstore-screenshot-01.png'),
        x: 5.8, y: 0.3, w: 3.2, h: 5.0, sizing: { type: 'contain', w: 3.2, h: 5.0 }
    });

    // Slide 7: Engine screenshot
    let s7 = pptx.addSlide();
    s7.background = { color: BLACK };
    s7.addImage({
        path: path.resolve(__dirname, 'engine-screenshot.png'),
        x: 0.8, y: 0.3, w: 3.2, h: 5.0, sizing: { type: 'contain', w: 3.2, h: 5.0 }
    });
    s7.addText('ONE-CLICK SETUP', {
        x: 4.8, y: 0.8, w: 4.8, h: 0.4, color: RED, fontSize: 14, charSpacing: 3
    });
    s7.addText('버튼 하나로\n서버 설치 완료', {
        x: 4.8, y: 1.3, w: 4.8, h: 1.5, color: WHITE, fontSize: 32, bold: true, lineSpacingMultiple: 1.3
    });
    s7.addText('사양 자동 검사\nUbuntu 24.04 LTS 설치\nDropbear SSH 자동 설정\n약 3분 소요', {
        x: 4.8, y: 3.0, w: 4.8, h: 1.5, color: GRAY, fontSize: 14, lineSpacingMultiple: 1.6
    });

    // Slide 8: 7-Layer Keep-Alive
    let s8 = pptx.addSlide();
    s8.background = { color: BLACK };
    s8.addText([
        { text: '7', options: { color: RED, fontSize: 28, bold: true } },
        { text: '계층 Keep-Alive 시스템', options: { color: WHITE, fontSize: 28, bold: true } }
    ], { x: 0, y: 0.3, w: 10, h: 0.7, align: 'center' });

    const layers = [
        ['1', 'Foreground Service', 'OS 프로세스 보호'],
        ['2', 'Wake Lock', '화면 꺼져도 CPU 유지'],
        ['3', 'WiFi Lock', '네트워크 상시 연결'],
        ['4', 'Battery Optimization Exempt', 'Doze 모드 우회'],
        ['5', 'Auto Restart', '종료 시 자동 재시작'],
        ['6', 'Boot Receiver', '재부팅 시 자동 시작'],
        ['7', 'Manufacturer Optimization', '삼성/샤오미/화웨이 대응']
    ];
    layers.forEach((l, i) => {
        const y = 1.2 + i * 0.55;
        s8.addShape(pptx.shapes.ROUNDED_RECTANGLE, {
            x: 1.5, y, w: 7, h: 0.45, fill: { color: DARK_GRAY }, rectRadius: 0.05
        });
        s8.addShape(pptx.shapes.OVAL, {
            x: 1.7, y: y + 0.06, w: 0.33, h: 0.33, fill: { color: RED }
        });
        s8.addText(l[0], {
            x: 1.7, y: y + 0.06, w: 0.33, h: 0.33, color: WHITE, fontSize: 11, align: 'center', valign: 'middle'
        });
        s8.addText(l[1], {
            x: 2.2, y, w: 3.5, h: 0.45, color: WHITE, fontSize: 13, valign: 'middle'
        });
        s8.addText(l[2], {
            x: 5.7, y, w: 2.5, h: 0.45, color: '666666', fontSize: 10, valign: 'middle'
        });
    });

    // Slide 9: Safety
    let s9 = pptx.addSlide();
    s9.background = { color: BLACK };
    s9.addText([
        { text: '안전이 ', options: { color: WHITE, fontSize: 36, bold: true } },
        { text: '최우선', options: { color: RED, fontSize: 36, bold: true } },
        { text: '입니다', options: { color: WHITE, fontSize: 36, bold: true } }
    ], { x: 0, y: 0.5, w: 10, h: 1, align: 'center' });

    const temps = [
        ['40°C', '정상', '서버 정상 가동', '22C55E'],
        ['45°C', '주의', '푸시 알림 경고', 'F59E0B'],
        ['50°C', '자동 정지', '서버 즉시 중단\n온도 복귀 시 재시작', RED]
    ];
    temps.forEach((t, i) => {
        const x = 1.2 + i * 2.8;
        s9.addShape(pptx.shapes.ROUNDED_RECTANGLE, {
            x, y: 1.8, w: 2.4, h: 2.8, fill: { color: DARK_GRAY }, rectRadius: 0.1
        });
        s9.addText(t[0], {
            x, y: 2.0, w: 2.4, h: 0.8, color: t[3], fontSize: 36, bold: true, align: 'center'
        });
        s9.addText(t[1], {
            x, y: 2.8, w: 2.4, h: 0.5, color: WHITE, fontSize: 16, bold: true, align: 'center'
        });
        s9.addText(t[2], {
            x, y: 3.4, w: 2.4, h: 0.8, color: GRAY, fontSize: 11, align: 'center', lineSpacingMultiple: 1.5
        });
    });

    // Slide 10: Cost comparison
    let s10 = pptx.addSlide();
    s10.background = { color: BLACK };
    s10.addText([
        { text: '비용 비교 — ', options: { color: WHITE, fontSize: 28, bold: true } },
        { text: '연간 기준', options: { color: RED, fontSize: 28, bold: true } }
    ], { x: 0, y: 0.4, w: 10, h: 0.8, align: 'center' });

    const tableData = [
        [
            { text: '솔루션', options: { fill: { color: RED }, color: WHITE, bold: true, fontSize: 14, align: 'center' } },
            { text: '하드웨어', options: { fill: { color: RED }, color: WHITE, bold: true, fontSize: 14, align: 'center' } },
            { text: '월 비용', options: { fill: { color: RED }, color: WHITE, bold: true, fontSize: 14, align: 'center' } },
            { text: '연간 총비용', options: { fill: { color: RED }, color: WHITE, bold: true, fontSize: 14, align: 'center' } }
        ],
        [
            { text: 'PocketServer', options: { fill: { color: DARK_GRAY }, color: WHITE, fontSize: 14, align: 'center' } },
            { text: '$0 (구형폰)', options: { fill: { color: DARK_GRAY }, color: WHITE, fontSize: 14, align: 'center' } },
            { text: '$0', options: { fill: { color: DARK_GRAY }, color: WHITE, fontSize: 14, align: 'center' } },
            { text: '$0', options: { fill: { color: DARK_GRAY }, color: RED, fontSize: 18, bold: true, align: 'center' } }
        ],
        [
            { text: 'VPS (클라우드)', options: { fill: { color: '111111' }, color: '666666', fontSize: 13, align: 'center' } },
            { text: '$0', options: { fill: { color: '111111' }, color: '666666', fontSize: 13, align: 'center' } },
            { text: '$4-6/월', options: { fill: { color: '111111' }, color: '666666', fontSize: 13, align: 'center', strike: true } },
            { text: '$48-72', options: { fill: { color: '111111' }, color: '666666', fontSize: 13, align: 'center', strike: true } }
        ],
        [
            { text: 'Raspberry Pi', options: { fill: { color: DARK_GRAY }, color: '666666', fontSize: 13, align: 'center' } },
            { text: '$50-100', options: { fill: { color: DARK_GRAY }, color: '666666', fontSize: 13, align: 'center', strike: true } },
            { text: '~$1', options: { fill: { color: DARK_GRAY }, color: '666666', fontSize: 13, align: 'center' } },
            { text: '$62-112', options: { fill: { color: DARK_GRAY }, color: '666666', fontSize: 13, align: 'center', strike: true } }
        ]
    ];
    s10.addTable(tableData, {
        x: 1.0, y: 1.6, w: 8, colW: [2.2, 1.8, 1.8, 2.2],
        rowH: [0.55, 0.6, 0.55, 0.55],
        border: { pt: 1, color: '333333' },
        valign: 'middle'
    });

    // Slide 11: Use cases
    let s11 = pptx.addSlide();
    s11.background = { color: BLACK };
    s11.addText([
        { text: '무엇이든 ', options: { color: WHITE, fontSize: 28, bold: true } },
        { text: '설치', options: { color: RED, fontSize: 28, bold: true } },
        { text: '할 수 있습니다', options: { color: WHITE, fontSize: 28, bold: true } }
    ], { x: 0, y: 0.3, w: 10, h: 0.8, align: 'center' });

    const useCases = [
        ['AI 에이전트', 'PicoClaw, OpenClaw\n24시간 자동 응답'],
        ['자동화 도구', 'n8n, Dify\n워크플로우 자동화'],
        ['웹 서버', '바이브코딩 웹앱\n개인 포트폴리오'],
        ['개발 서버', 'Git, Node.js, Python\n테스트 환경'],
        ['스마트홈 허브', 'Home Assistant\nIoT 제어'],
        ['파일 서버', '개인 클라우드\nNAS 대용']
    ];
    useCases.forEach((uc, i) => {
        const col = i % 3;
        const row = Math.floor(i / 3);
        const x = 1.0 + col * 2.8;
        const y = 1.4 + row * 2.0;
        s11.addShape(pptx.shapes.ROUNDED_RECTANGLE, {
            x, y, w: 2.4, h: 1.6, fill: { color: DARK_GRAY }, rectRadius: 0.1
        });
        s11.addText(uc[0], {
            x, y: y + 0.15, w: 2.4, h: 0.5, color: WHITE, fontSize: 16, bold: true, align: 'center'
        });
        s11.addText(uc[1], {
            x, y: y + 0.7, w: 2.4, h: 0.7, color: '888888', fontSize: 10, align: 'center', lineSpacingMultiple: 1.4
        });
    });

    // Slide 12: CTA
    let s12 = pptx.addSlide();
    s12.background = { color: BLACK };
    s12.addText([
        { text: '서랍 속 스마트폰을\n', options: { color: WHITE, fontSize: 42, bold: true } },
        { text: '깨워주세요', options: { color: RED, fontSize: 42, bold: true } }
    ], { x: 1, y: 1.0, w: 8, h: 2.2, align: 'center', lineSpacingMultiple: 1.3 });
    s12.addText('PocketMonitor — Google Play Store\nPocketEngine — pocket-server-palank.web.app', {
        x: 1, y: 3.2, w: 8, h: 0.9, color: GRAY, fontSize: 16, align: 'center', lineSpacingMultiple: 1.5
    });
    s12.addText('전 기능 무료 · 광고만으로 운영 · 추가 비용 $0', {
        x: 1, y: 4.2, w: 8, h: 0.5, color: '3B82F6', fontSize: 14, align: 'center'
    });

    // Save
    const outputPath = path.resolve(__dirname, 'PocketServer-Presentation.pptx');
    await pptx.writeFile({ fileName: outputPath });
    console.log('Presentation saved to: ' + outputPath);
}

createPresentation().catch(console.error);
