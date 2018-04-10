
#ifndef __TRACKINGM_H
#define __TRACKINGM_H

#include "gendefs.h"

class TrackingService;

void TrackingPeriodicTask( void );
void TrackingInit( void );
void TrackingRport(void);
void TrackingHandleTimeChange( s32 TimeDifference );
void TrackingInternalRun (u8 TrackingOption, u16 TotalTrackingDuration, u16 MsgTrackingTmrWhileMov, u32 MsgTrackingTmrWhileNotDriving);

#endif
