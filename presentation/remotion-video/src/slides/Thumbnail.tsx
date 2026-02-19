import React from 'react';
import {AbsoluteFill, Img, staticFile} from 'remotion';

export const YouTubeThumbnail: React.FC = () => {
  return (
    <AbsoluteFill style={{backgroundColor: '#000'}}>
      {/* Red accent strip left */}
      <div style={{
        position: 'absolute', left: 0, top: 0, bottom: 0,
        width: 12, backgroundColor: '#EB0028',
      }} />

      {/* Main content area */}
      <div style={{
        display: 'flex', flexDirection: 'row', height: '100%',
        alignItems: 'center', paddingLeft: 60,
      }}>
        {/* Text side */}
        <div style={{flex: 1.2, paddingLeft: 40}}>
          {/* Badge */}
          <div style={{
            display: 'inline-flex', backgroundColor: '#EB0028',
            borderRadius: 6, padding: '8px 18px', marginBottom: 20,
          }}>
            <div style={{fontSize: 22, fontWeight: 'bold', color: '#fff', fontFamily: 'Arial', letterSpacing: 2}}>
              무료 앱
            </div>
          </div>

          {/* Main title */}
          <div style={{
            fontSize: 78, fontWeight: 'bold', color: '#fff',
            fontFamily: 'Arial', lineHeight: 1.2, marginBottom: 15,
          }}>
            서랍 속 스마트폰이
          </div>
          <div style={{
            fontSize: 78, fontWeight: 'bold', color: '#EB0028',
            fontFamily: 'Arial', lineHeight: 1.2,
          }}>
            나만의 서버가 됩니다
          </div>

          {/* Subtitle */}
          <div style={{
            fontSize: 32, color: '#999', fontFamily: 'Arial',
            marginTop: 25, lineHeight: 1.4,
          }}>
            원클릭 Ubuntu 설치 · 24시간 가동 · 비용 $0
          </div>

          {/* Brand */}
          <div style={{
            display: 'flex', alignItems: 'center', gap: 15, marginTop: 35,
          }}>
            <div style={{
              width: 50, height: 50, borderRadius: 25,
              border: '3px solid #EB0028', display: 'flex',
              justifyContent: 'center', alignItems: 'center',
            }}>
              <div style={{fontSize: 26, fontWeight: 'bold', color: '#EB0028', fontFamily: 'Arial'}}>P</div>
            </div>
            <div style={{fontSize: 28, fontWeight: 'bold', color: '#fff', fontFamily: 'Arial', letterSpacing: 3}}>
              POCKET<span style={{color: '#EB0028'}}>SERVER</span>
            </div>
          </div>
        </div>

        {/* Screenshot side */}
        <div style={{flex: 0.8, display: 'flex', justifyContent: 'center', alignItems: 'center'}}>
          <div style={{
            position: 'relative',
            transform: 'rotate(-5deg)',
          }}>
            <Img
              src={staticFile('monitor-screenshot.png')}
              style={{
                height: 820, objectFit: 'contain',
                borderRadius: 20,
                boxShadow: '0 20px 60px rgba(235, 0, 40, 0.3)',
              }}
            />
          </div>
        </div>
      </div>

      {/* Bottom red accent */}
      <div style={{
        position: 'absolute', bottom: 0, left: 0, right: 0,
        height: 6, backgroundColor: '#EB0028',
      }} />
    </AbsoluteFill>
  );
};
