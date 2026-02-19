import React from 'react';
import {interpolate, spring, useCurrentFrame, useVideoConfig} from 'remotion';

export const FadeIn: React.FC<{
  children: React.ReactNode;
  delay?: number;
  direction?: 'up' | 'down' | 'left' | 'right' | 'none';
  distance?: number;
}> = ({children, delay = 0, direction = 'up', distance = 40}) => {
  const frame = useCurrentFrame();
  const {fps} = useVideoConfig();

  const progress = spring({
    fps,
    frame: frame - delay,
    config: {damping: 20, stiffness: 80, mass: 0.8},
    durationInFrames: 30,
  });

  const opacity = interpolate(progress, [0, 1], [0, 1], {
    extrapolateRight: 'clamp',
  });

  let translateX = 0;
  let translateY = 0;
  if (direction === 'up') translateY = interpolate(progress, [0, 1], [distance, 0]);
  if (direction === 'down') translateY = interpolate(progress, [0, 1], [-distance, 0]);
  if (direction === 'left') translateX = interpolate(progress, [0, 1], [distance, 0]);
  if (direction === 'right') translateX = interpolate(progress, [0, 1], [-distance, 0]);

  return (
    <div style={{opacity, transform: `translate(${translateX}px, ${translateY}px)`}}>
      {children}
    </div>
  );
};

export const ScaleIn: React.FC<{
  children: React.ReactNode;
  delay?: number;
}> = ({children, delay = 0}) => {
  const frame = useCurrentFrame();
  const {fps} = useVideoConfig();

  const scale = spring({
    fps,
    frame: frame - delay,
    config: {damping: 12, stiffness: 100, mass: 0.6},
    durationInFrames: 25,
  });

  const opacity = interpolate(scale, [0, 0.5], [0, 1], {
    extrapolateRight: 'clamp',
  });

  return (
    <div style={{opacity, transform: `scale(${scale})`}}>
      {children}
    </div>
  );
};

export const CountUp: React.FC<{
  target: string;
  delay?: number;
  style?: React.CSSProperties;
}> = ({target, delay = 0, style}) => {
  const frame = useCurrentFrame();
  const {fps} = useVideoConfig();

  const progress = spring({
    fps,
    frame: frame - delay,
    config: {damping: 30, stiffness: 60},
    durationInFrames: 45,
  });

  const opacity = interpolate(progress, [0, 0.3], [0, 1], {
    extrapolateRight: 'clamp',
  });

  return <span style={{...style, opacity}}>{target}</span>;
};

export const SlideTransition: React.FC<{
  children: React.ReactNode;
  durationInFrames: number;
}> = ({children, durationInFrames}) => {
  const frame = useCurrentFrame();

  const exitOpacity = interpolate(
    frame,
    [durationInFrames - 15, durationInFrames],
    [1, 0],
    {extrapolateLeft: 'clamp', extrapolateRight: 'clamp'}
  );

  return <div style={{opacity: exitOpacity}}>{children}</div>;
};
