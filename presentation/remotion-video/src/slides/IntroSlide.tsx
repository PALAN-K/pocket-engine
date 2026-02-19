import React from 'react';
import {AbsoluteFill, useCurrentFrame, spring, useVideoConfig, interpolate} from 'remotion';

export const IntroSlide: React.FC = () => {
  const frame = useCurrentFrame();
  const {fps} = useVideoConfig();

  // Phase 1: Red dot appears at center and pulses (frame 0-20)
  const dotProgress = spring({fps, frame, config: {damping: 10, stiffness: 120, mass: 0.4}, durationInFrames: 15});
  const dotScale = interpolate(dotProgress, [0, 1], [0, 1]);
  const dotPulse = frame > 10 && frame < 30 ? 1 + 0.15 * Math.sin((frame - 10) * 0.4) : 1;

  // Phase 2: Dot expands into circle ring (frame 20-40)
  const ringProgress = spring({fps, frame: frame - 20, config: {damping: 14, stiffness: 80}, durationInFrames: 25});
  const ringScale = interpolate(ringProgress, [0, 1], [0.1, 1]);
  const ringOpacity = interpolate(ringProgress, [0, 0.2], [0, 1], {extrapolateRight: 'clamp'});
  const dotFade = interpolate(frame, [20, 30], [1, 0], {extrapolateLeft: 'clamp', extrapolateRight: 'clamp'});

  // Phase 3: P letter appears inside (frame 35-50)
  const letterProgress = spring({fps, frame: frame - 35, config: {damping: 15, stiffness: 90}, durationInFrames: 20});
  const letterOpacity = interpolate(letterProgress, [0, 0.3], [0, 1], {extrapolateRight: 'clamp'});
  const letterScale = interpolate(letterProgress, [0, 1], [0.5, 1]);

  // Phase 4: Short accent lines extend from circle (frame 40-55)
  const accentProgress = spring({fps, frame: frame - 40, config: {damping: 20, stiffness: 70}, durationInFrames: 25});
  const accentWidth = interpolate(accentProgress, [0, 1], [0, 120]);
  const accentOpacity = interpolate(accentProgress, [0, 0.2], [0, 0.6], {extrapolateRight: 'clamp'});

  // Phase 5: Brand text slides up (frame 55-75)
  const textProgress = spring({fps, frame: frame - 55, config: {damping: 18, stiffness: 70}, durationInFrames: 25});
  const textOpacity = interpolate(textProgress, [0, 0.4], [0, 1], {extrapolateRight: 'clamp'});
  const textY = interpolate(textProgress, [0, 1], [40, 0]);

  // Phase 6: Tagline fades in (frame 70-90)
  const tagProgress = spring({fps, frame: frame - 70, config: {damping: 20}, durationInFrames: 25});
  const tagOpacity = interpolate(tagProgress, [0, 0.5], [0, 1], {extrapolateRight: 'clamp'});
  const tagY = interpolate(tagProgress, [0, 1], [20, 0]);

  // Subtle glow pulse on the ring after everything settles
  const glowIntensity = frame > 55 ? 0.25 + 0.1 * Math.sin((frame - 55) * 0.06) : 0;

  return (
    <AbsoluteFill style={{backgroundColor: '#000', justifyContent: 'center', alignItems: 'center'}}>
      {/* Vertical red accent line - left, very subtle */}
      <div style={{
        position: 'absolute', left: 80, top: '50%',
        width: 2, height: accentWidth * 1.5, backgroundColor: '#EB0028',
        opacity: accentOpacity * 0.4, transform: 'translateY(-50%)',
      }} />
      {/* Vertical red accent line - right */}
      <div style={{
        position: 'absolute', right: 80, top: '50%',
        width: 2, height: accentWidth * 1.5, backgroundColor: '#EB0028',
        opacity: accentOpacity * 0.4, transform: 'translateY(-50%)',
      }} />

      {/* Red dot that appears first */}
      <div style={{
        position: 'absolute',
        width: 12, height: 12, borderRadius: 6,
        backgroundColor: '#EB0028',
        transform: `scale(${dotScale * dotPulse})`,
        opacity: dotFade,
      }} />

      {/* Logo circle ring */}
      <div style={{
        width: 130, height: 130, borderRadius: 65,
        border: '3px solid #EB0028', display: 'flex',
        justifyContent: 'center', alignItems: 'center',
        transform: `scale(${ringScale})`, opacity: ringOpacity,
        boxShadow: `0 0 ${35 * glowIntensity}px ${15 * glowIntensity}px rgba(235, 0, 40, ${glowIntensity})`,
      }}>
        {/* P letter */}
        <div style={{
          fontSize: 54, fontWeight: 'bold', color: '#EB0028',
          fontFamily: 'Arial', opacity: letterOpacity,
          transform: `scale(${letterScale})`,
        }}>
          P
        </div>
      </div>

      {/* Short horizontal accent lines from circle */}
      <div style={{
        position: 'absolute', top: '50%', left: '50%',
        transform: 'translate(-50%, -50%)',
        display: 'flex', alignItems: 'center', justifyContent: 'center',
        width: 1920, pointerEvents: 'none',
      }}>
        {/* Left accent */}
        <div style={{
          position: 'absolute', right: 'calc(50% + 85px)',
          width: accentWidth, height: 2, backgroundColor: '#EB0028',
          opacity: accentOpacity,
        }} />
        {/* Right accent */}
        <div style={{
          position: 'absolute', left: 'calc(50% + 85px)',
          width: accentWidth, height: 2, backgroundColor: '#EB0028',
          opacity: accentOpacity,
        }} />
      </div>

      {/* Brand text */}
      <div style={{
        opacity: textOpacity, transform: `translateY(${textY}px)`,
        marginTop: 30, textAlign: 'center',
      }}>
        <div style={{
          fontSize: 48, fontWeight: 'bold', color: '#fff',
          fontFamily: 'Arial', letterSpacing: 8,
        }}>
          POCKET<span style={{color: '#EB0028'}}>SERVER</span>
        </div>
      </div>

      {/* Tagline */}
      <div style={{
        opacity: tagOpacity, marginTop: 18, textAlign: 'center',
        transform: `translateY(${tagY}px)`,
      }}>
        <div style={{
          fontSize: 22, color: '#777', fontFamily: 'Arial', letterSpacing: 2,
        }}>
          서랍 속 스마트폰이 나만의 서버가 됩니다
        </div>
      </div>
    </AbsoluteFill>
  );
};
