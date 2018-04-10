#ifndef __FIFO_H__
#define __FIFO_H__

#include "gendefs.h"

#define FIFO_WATERMARK_ENABLE 	1

#ifdef __cplusplus
extern "C" {
#endif

/******************************************************************************
* Types
******************************************************************************/

#if FIFO_WATERMARK_ENABLE
#   define FIFO_INIT(buffer,size)		{ (u8*)&buffer[0], 0, 0, size, 0 }
#   define FIFO_INIT2(varname,type,count) \
    varname.Buffer = varname##_buffer; \
    varname.In     = 0; \
    varname.Out    = 0; \
    varname.HighWaterMark = 0; \
    varname.Size   = (sizeof(type)*(count) + 1);
#   define FIFO_DYN_INIT(fifoname,type,count) \
	fifoname.Buffer = new type[count]; \
	fifoname.In		= 0; \
	fifoname.Out	= 0; \
	fifoname.HighWaterMark = 0; \
	fifoname.Size	= (sizeof(type)*(count) + 1);
#else
#   define FIFO_INIT(buffer,size)		{ (u8*)&buffer[0], 0, 0, size }
#   define FIFO_INIT2(varname,type,count) \
    varname.Buffer = varname##_buffer; \
    varname.In     = 0; \
    varname.Out    = 0; \
    varname.Size   = (sizeof(type)*(count) + 1);
#   define FIFO_DYN_INIT(fifoname,type,count) \
	fifoname.Buffer = new type[count]; \
	fifoname.In		= 0; \
	fifoname.Out	= 0; \
	fifoname.Size	= (sizeof(type)*(count) + 1);
#endif

#define FIFO_CREATE2(varname,type,count)  \
	u8 varname ## _buffer [(sizeof(type)*(count))+1];  \
	SFIFO varname

typedef enum  {
	FIFO_Ok,
	FIFO_NoData,
	FIFO_OutOfBounds,
	FIFO_Full,
	FIFO_Overflow,
	FIFO_Empty,
	FIFO_Underflow,
	FIFO_Undefined			= 0xffff,
}EFIFO_Result;

typedef struct  {
	u8* Buffer;
	volatile u16 In;
	volatile u16 Out;
	u16 Size;
#if FIFO_WATERMARK_ENABLE
	u16 HighWaterMark;
#endif
} SFIFO;

/******************************************************************************
* Interface definition
******************************************************************************/

EFIFO_Result FIFO_TouchBytePtr(const SFIFO* fifo, u8** ptr, u16 offset);
EFIFO_Result FIFO_TouchByte(const SFIFO* fifo, u8* byte, u16 offset);

// Add a single byte to the end of the FIFO
EFIFO_Result FIFO_AddByte(SFIFO* fifo, u8 byte);

// Add an array of bytes to end of the FIFO
EFIFO_Result FIFO_AddData(SFIFO* fifo, const u8* data, u16 length);

// Remove a single byte from the head of the FIFO
EFIFO_Result FIFO_GetByte(SFIFO* fifo, u8* byte);

// Removes an array of bytes from the head of the FIFO
EFIFO_Result FIFO_GetData(SFIFO* fifo, u8* data, u16 length);

// Removes count bytes from the head of the FIFO - without returning them
EFIFO_Result FIFO_DiscardData(SFIFO* fifo, u16 count);

// Get an array of bytes from the head FIFO without modifying the FIFO (data is still kept on the FIFO)
EFIFO_Result FIFO_TouchData(SFIFO* fifo, u8* byte, u16 size, u16 offset);

// Modify data on the FIFO - without modifying the size of the FIFO (changes inplace)
EFIFO_Result FIFO_UpdateData(SFIFO* fifo, u8* byte, u16 size, u16 offset);

// Get the amount of bytes in the FIFO
u16 FIFO_GetCount(const SFIFO* fifo);

// Removes all data from the FIFO
void FIFO_Clear(SFIFO* fifo);

// true if the size of the FIFO is "0" - there is not data on the FIFO
bool FIFO_IsFull(const SFIFO* fifo);

#if FIFO_WATERMARK_ENABLE
// gets the hightest size of the FIFO (gets updated when you call  FIFO_AddByte())
u16 FIFO_GetWatermark(const SFIFO* fifo);
#endif

#ifdef __cplusplus
}
#endif

#endif // __FIFO_H__

