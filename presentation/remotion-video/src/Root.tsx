import React from 'react';
import {Composition, Still} from 'remotion';
import {PocketServerPresentation} from './Composition';
import {YouTubeThumbnail} from './slides/Thumbnail';

// Intro(120) + 10 slides(150×10=1500) + Arch(180) + KeepAlive(180) + CTA(180) + Outro(180)
// = 120 + 150*8 + 180*4 + 150*2 = 120+1200+720+300 = let's just calculate:
// 120+150+150+150+150+180+150+150+180+150+150+150+180+180 = 2190
const TOTAL = 2190;

export const RemotionRoot: React.FC = () => {
  return (
    <>
      <Composition
        id="PocketServerPresentation"
        component={PocketServerPresentation}
        durationInFrames={TOTAL}
        fps={30}
        width={1920}
        height={1080}
      />
      <Still
        id="YouTubeThumbnail"
        component={YouTubeThumbnail}
        width={1920}
        height={1080}
      />
    </>
  );
};
