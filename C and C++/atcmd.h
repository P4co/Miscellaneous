
#ifndef __ATCMD_H__
#define __ATCMD_H__

#include "gendefs.h"
#include "objfifo.h"
#include "preconfig.h"
#include "BaseUart.h"
#include <stdarg.h>

#define ATC_VERBOSE								0

/******************************************************************************
* Internal definitions
******************************************************************************/

#if defined (__USE_MODEM)
#define ATC_MSG_MAX_LENGTH						2048

#define ATC_COMMAND_BUFFER_MAX_LENGTH			120
#define ATC_RESPONSE_BUFFER_MAX_LENGTH			32
#define ATC_EVENT_BUFFER_MAX_LENGTH				32
#define ATC_ERROR_BUFFER_MAX_LENGTH				32
#define ATC_COMMAND_DATA_BUFFER_MAX_LENGTH		200
#define ATC_EVENT_DATA_BUFFER_MAX_LENGTH		64

#define ATC_COMMAND_FIFO_SIZE					20
#define ATC_COMMAND_FIFO_LENGTH					(ATC_COMMAND_FIFO_SIZE*sizeof(SATC_CommandData))

#define ATC_EVENT_FIFO_SIZE						20
#define ATC_EVENT_FIFO_LENGTH					(ATC_EVENT_FIFO_SIZE*sizeof(SATC_EventData))
#else
#define ATC_MSG_MAX_LENGTH						10

#define ATC_COMMAND_BUFFER_MAX_LENGTH			1
#define ATC_RESPONSE_BUFFER_MAX_LENGTH			1
#define ATC_EVENT_BUFFER_MAX_LENGTH				1
#define ATC_ERROR_BUFFER_MAX_LENGTH				1
#define ATC_COMMAND_DATA_BUFFER_MAX_LENGTH		1
#define ATC_EVENT_DATA_BUFFER_MAX_LENGTH		1

#define ATC_COMMAND_FIFO_SIZE					1
#define ATC_COMMAND_FIFO_LENGTH					(ATC_COMMAND_FIFO_SIZE*sizeof(SATC_CommandData))

#define ATC_EVENT_FIFO_SIZE						1
#define ATC_EVENT_FIFO_LENGTH					(ATC_EVENT_FIFO_SIZE*sizeof(SATC_EventData))

#endif

#define ATC_CMD_SENT_TO_PROC_DELAY_ms			3

#define ATC_COMMAND_LIFETIME_ms					5000

#define ATC_MSG_END								{'\r','\n'}
#define ATC_MSG_END_LENGTH						2

#define ATC_PROMPT								{'>',' '}
#define ATC_PROMPT_LENGTH						2

#if defined (__A1) || defined (__F1)
#define ATC_GPRS_PROMPT							{'@'}
#define ATC_GPRS_PROMPT_LENGTH					1
#define ATC_FILE_PROMPT							{'\n','>'}
#define ATC_FILE_PROMPT_LENGTH					2
#define ATC_CF_PROMPT							{'>'}
#define ATC_CF_PROMPT_LENGTH					1
#endif

#define ATC_CR									0x0d
#define ATC_CTRL_Z								0x1a
#define ATC_ESC									0x1b

#define ATC_MSG_OK_STR							"OK"

#if defined (__A1) || defined (__F1)
#define ATC_LAST_DATA_SAVE_SIZE					128
#else
#define ATC_LAST_DATA_SAVE_SIZE					10
#endif

/******************************************************************************
* Types definition
******************************************************************************/

typedef enum {
	ATC_Ok						= 0x0000,
	ATC_NotInitialized			= 0x0001,
	ATC_SendFailed				= 0x0002,
	ATC_FifoOverflow			= 0x0004,
	ATC_FifoUnderflow			= 0x0008,
	ATC_NotFound				= 0x0010,
	ATC_InProc					= 0x0020,
	ATC_NoData					= 0x0040,
	ATC_IncompleteData			= 0x0080,
	ATC_EmptyMessage			= 0x0100,
	ATC_UndefinedMessage		= 0x0200,
	ATC_ReceiveError			= 0x0400,
	ATC_EventError				= 0x0800,
	ATC_InitError				= 0x1000,
	ATC_DataError				= 0x2000,
	ATC_Error					= 0xffff,
} EATC_Result;

typedef enum {
	EAR_Undefined,
	EAR_None,
	EAR_Data,
	EAR_Ok,
	EAR_Timeout,
	EAR_Error,
} EATC_Response;

typedef EATC_Result (*FATC_InitCallback) (void);
typedef EATC_Result (*FATC_ResponseCallback) (u16 id, u8* data, u16 length);
typedef void (*FATC_DebugCallback) (u8* data, u16 length);

typedef struct {
	const char* Command;
	const char* Response;
	u32 Timeout;
	FATC_ResponseCallback ResponseCallback;
} SATC_Command;

typedef struct {
	u16 CommandId;
	char Buffer[ATC_COMMAND_DATA_BUFFER_MAX_LENGTH];
	u16 SequenceNum;
	u32 SentTime_ms;
	EATC_Response Response;
	FATC_ResponseCallback ResponseCallback;
	bool Disposed;
} SATC_CommandData;

typedef struct {
	const char* Event;
	FATC_ResponseCallback EventCallback;
} SATC_Event;

typedef struct {
	const char* Error;
	FATC_ResponseCallback ErrorCallback;
} SATC_Error;

typedef struct {
	u16 EventId;
	s8 Data[ATC_EVENT_DATA_BUFFER_MAX_LENGTH];
	u16 DataLength;
	bool Dispose;
} SATC_EventData;

typedef struct {
	BaseUart* UART;
	SOBJFIFO* CMD_FIFO;
	SOBJFIFO* EVT_FIFO;
	const SATC_Command* Commands;
	u16 CommandsCount;
	SATC_Event* Events;
	u16 EventsCount;
	const SATC_Error* Errors;
	u16 ErrorsCount;
    char DataPromptSymbol;
	u32 NextActivityDelay_ms;
	u32 LastActivityTime_ms;
	u16 SequenceNum;
	FATC_InitCallback InitCallback;
	FATC_DebugCallback DebugCallback;
} SATC;

/******************************************************************************
* Interface declaration
******************************************************************************/

void ATC_Init(SATC* atc);
u16 ATC_SendCommand(SATC* atc, bool dispose, u16 cmdid, va_list args);
u16 ATC_SendCommandOverride(SATC* atc, bool dispose, FATC_ResponseCallback callback, u16 cmdid, va_list args);
u16 ATC_SendUrgentCommand(SATC* atc, bool dispose, u16 cmdid, va_list args);
u16 ATC_SendUrgentCommandOverride(SATC* atc, bool dispose, FATC_ResponseCallback callback, u16 cmdid, va_list args);
FATC_ResponseCallback ATC_SetEventCallback(SATC* atc, u16 evtid, FATC_ResponseCallback callback);
u16 ATC_GetFreeCommandsSpace(SATC* atc);
EATC_Result ATC_GetCommandResponse(SATC* atc, u32 seqnum, EATC_Response* response);
EATC_Result ATC_ClosePendingCommand(SATC* atc);
EATC_Result ATC_OnSendReceive(SATC* atc);
bool ATC_IsCommandsDisposed(SATC* atc);
bool ATC_IsEventsServiced(SATC* atc);
void ATC_DisposeAllCommands(SATC* atc);
void ATC_DisposeAllEvents(SATC* atc);
u8*  ATC_GetLastReceivedData( void );
void ATC_SetEnableModemShowComm( bool state );
bool ATC_GetEnableModemShowComm( void );
void ATC_SetAutoEnableModemShowComm( bool state );
bool ATC_GetAutoEnableModemShowComm( void );

void ATC_DisposePendingCommand(SATC* atc);
void ATC_DisposeCommandWithSeqNum(SATC* atc, u16* seqnum);

#endif // __ATCMD_H__

