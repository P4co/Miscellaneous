#include "fifo.h"
#include <stdlib.h>

#ifndef  __disable_interrupt
void __disable_interrupt(){}
#endif

#ifndef  __enable_interrupt
void __enable_interrupt(){}
#endif

/******************************************************************************
* Internal prototypes
******************************************************************************/

void FIFO_Push(SFIFO* fifo, u8 byte);
u8 FIFO_Pull(SFIFO* fifo);

/******************************************************************************
* Internal implementation
******************************************************************************/

void FIFO_Push(SFIFO* fifo, u8 byte)
{
	fifo->Buffer[fifo->In] = byte;

	if (++fifo->In == fifo->Size) {
		fifo->In = 0;
	}
#if FIFO_WATERMARK_ENABLE
	fifo->HighWaterMark = MAX( FIFO_GetCount( fifo ), fifo->HighWaterMark );
#endif
}

u8 FIFO_Pull(SFIFO* fifo)
{
	u8 byte = fifo->Buffer[fifo->Out];

	if (++fifo->Out == fifo->Size) {
		fifo->Out = 0;
	}

	return byte;
}

/******************************************************************************
* Interface implementation
******************************************************************************/

EFIFO_Result FIFO_TouchBytePtr(const SFIFO* fifo, u8** ptr, u16 offset)
{
	if (offset >= FIFO_GetCount(fifo)) {
		return FIFO_OutOfBounds;
	}

    u32 readOffset = fifo->Out;

	*ptr = (u8*)&fifo->Buffer[readOffset + offset - ((readOffset + offset >= fifo->Size) ? fifo->Size : 0)];

	return FIFO_Ok;
}

EFIFO_Result FIFO_TouchByte(const SFIFO* fifo, u8* byte, u16 offset)
{
	EFIFO_Result eResult;
	u8* byteptr;

	if ((eResult = FIFO_TouchBytePtr(fifo, (u8**)&byteptr, offset)) == FIFO_Ok) {
		*byte = *byteptr;
	}

	return eResult;
}

EFIFO_Result FIFO_AddByte(SFIFO* fifo, u8 byte)
{
	if ( FIFO_IsFull( fifo ) )
	{
		return FIFO_Full;
	}

	FIFO_Push(fifo, byte);

	return FIFO_Ok;
}

EFIFO_Result FIFO_AddData(SFIFO* fifo, const u8* data, u16 length)
{
	if (data == NULL || length == 0) {
		return FIFO_NoData;
	}

	if (FIFO_GetCount(fifo) + length >= fifo->Size) {
		return FIFO_Overflow;
	}

	while (length--) {
		FIFO_Push(fifo, *data++);
	}

	return FIFO_Ok;
}

EFIFO_Result FIFO_GetByte(SFIFO* fifo, u8* byte)
{
	if (FIFO_GetCount(fifo) == 0) {
		return FIFO_Empty;
	}
	*byte = FIFO_Pull(fifo);

	return FIFO_Ok;
}

EFIFO_Result FIFO_GetData(SFIFO* fifo, u8* data, u16 length)
{
	EFIFO_Result eResult = FIFO_Ok;

	if (FIFO_GetCount(fifo) < length) {
		eResult = FIFO_Underflow;
	}
	else 
	{
		while (length--)
		{
			if (FIFO_GetByte(fifo, data++) != FIFO_Ok)
			{
				return eResult;
			}
		}
	}
return eResult;
}

EFIFO_Result FIFO_DiscardData(SFIFO* fifo, u16 count)
{
	EFIFO_Result eResult = FIFO_Ok;
	u8 Dummy;

	for ( ; count > 0; count-- )
	{
		if ( ( eResult = FIFO_GetByte( fifo, &Dummy) ) != FIFO_Ok )
		{
			return FIFO_Underflow;
		}
	}
	return eResult;
}

u16 FIFO_GetCount(const SFIFO* fifo)
{
	// We must copy the pointers to local variables, because we need them to remain with the same
	// value for this function, though they could be changed from interrupt context.
	u16 in = fifo->In;
	u16 out = fifo->Out;

	return (in < out ? fifo->Size : 0) + in - out;
}

void FIFO_Clear(SFIFO* fifo)
{
	fifo->In = fifo->Out = 0;
}

bool FIFO_IsFull(const SFIFO* fifo)
{
	return ( FIFO_GetCount(fifo) == ( fifo->Size - 1 ) );
}

EFIFO_Result FIFO_TouchData(SFIFO* fifo, u8* byte, u16 size, u16 offset)
{
    EFIFO_Result r;
    while(size!=0) {
        r = FIFO_TouchByte(fifo,byte,offset);
        if (r!=FIFO_Ok)
            return r;
        size   --;
        offset ++;
        byte   ++;
    }
    return FIFO_Ok;
}

EFIFO_Result FIFO_UpdateData(SFIFO* fifo, u8* byte, u16 size, u16 offset)
{
    EFIFO_Result r;
    u8* byteptr;

    while(size!=0) {
        r = FIFO_TouchBytePtr(fifo, (u8**)&byteptr, offset);
        if (r == FIFO_Ok) {
            *byteptr = *byte;
        }
        else
            return r;
        size   --;
        offset ++;
        byte   ++;
    }
    return FIFO_Ok;
}

u16 FIFO_GetWatermark(const SFIFO* fifo)
{
	return fifo->HighWaterMark;
}

