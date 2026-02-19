import React from 'react';
import {AbsoluteFill, Sequence, useCurrentFrame, interpolate} from 'remotion';
import {IntroSlide} from './slides/IntroSlide';
import {TitleSlide} from './slides/TitleSlide';
import {ProblemSlide} from './slides/ProblemSlide';
import {InsightSlide} from './slides/InsightSlide';
import {SolutionSlide} from './slides/SolutionSlide';
import {ArchitectureSlide} from './slides/ArchitectureSlide';
import {MonitorSlide} from './slides/MonitorSlide';
import {EngineSlide} from './slides/EngineSlide';
import {KeepAliveSlide} from './slides/KeepAliveSlide';
import {SafetySlide} from './slides/SafetySlide';
import {CostSlide} from './slides/CostSlide';
import {UseCasesSlide} from './slides/UseCasesSlide';
import {CTASlide} from './slides/CTASlide';
import {OutroSlide} from './slides/OutroSlide';

const SLIDE_DURATION = 150; // 5 seconds at 30fps
const FADE_FRAMES = 12;

const SlideWrapper: React.FC<{children: React.ReactNode; duration: number; noFadeOut?: boolean}> = ({children, duration, noFadeOut}) => {
  const frame = useCurrentFrame();

  const enterOpacity = interpolate(frame, [0, FADE_FRAMES], [0, 1], {
    extrapolateLeft: 'clamp', extrapolateRight: 'clamp',
  });
  const exitOpacity = noFadeOut ? 1 : interpolate(frame, [duration - FADE_FRAMES, duration], [1, 0], {
    extrapolateLeft: 'clamp', extrapolateRight: 'clamp',
  });

  return (
    <AbsoluteFill style={{opacity: Math.min(enterOpacity, exitOpacity)}}>
      {children}
    </AbsoluteFill>
  );
};

const slides = [
  {component: IntroSlide, duration: 120},       // 4s — Intro
  {component: TitleSlide, duration: SLIDE_DURATION},
  {component: ProblemSlide, duration: SLIDE_DURATION},
  {component: InsightSlide, duration: SLIDE_DURATION},
  {component: SolutionSlide, duration: SLIDE_DURATION},
  {component: ArchitectureSlide, duration: 180}, // 6s
  {component: MonitorSlide, duration: SLIDE_DURATION},
  {component: EngineSlide, duration: SLIDE_DURATION},
  {component: KeepAliveSlide, duration: 180},    // 6s
  {component: SafetySlide, duration: SLIDE_DURATION},
  {component: CostSlide, duration: SLIDE_DURATION},
  {component: UseCasesSlide, duration: SLIDE_DURATION},
  {component: CTASlide, duration: 180},          // 6s
  {component: OutroSlide, duration: 180},        // 6s — Outro (has internal fade-out)
];

// Calculate total duration
const TOTAL_FRAMES = slides.reduce((sum, s) => sum + s.duration, 0);

export const PocketServerPresentation: React.FC = () => {
  let currentFrame = 0;

  return (
    <AbsoluteFill style={{backgroundColor: '#000'}}>
      {slides.map((slide, i) => {
        const SlideComponent = slide.component;
        const from = currentFrame;
        const isLast = i === slides.length - 1;
        currentFrame += slide.duration;

        return (
          <Sequence key={i} from={from} durationInFrames={slide.duration}>
            <SlideWrapper duration={slide.duration} noFadeOut={isLast}>
              <SlideComponent />
            </SlideWrapper>
          </Sequence>
        );
      })}
    </AbsoluteFill>
  );
};
