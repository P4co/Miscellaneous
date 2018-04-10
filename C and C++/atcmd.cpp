#include "atcmd.h"
#include "errors.h"
#include "systmr.h"
#include "log.h"
#include <string.h>
#include <stdio.h>

#if ATC_VERBOSE

#include "log.h"

#if defined (__ENGINEERING_TEST)

#include "comm.h"

#endif

#endif

/******************************************************************************
* Internal variables
******************************************************************************/

__no_init static struct {
	u8 Data[ATC_MSG_MAX_LENGTH];
	u16 Length;
} ATC_ReceiveBuffer;

__no_init static u8 LastReceivedData[ATC_LAST_DATA_SAVE_SIZE];

static struct {
	bool Enable;
	bool AutoEnable;
}ModemShowComm = {false, false};

/******************************************************************************
* Internal prototypes
******************************************************************************/

bool ATC_IsCommandInProc(SATC_CommandData* cmd);

EATC_Result ATC_GetCommandToSend(SATC* atc, SATC_CommandData** cmdptr);
EATC_Result ATC_GetPendingCommand(SATC* atc, SATC_CommandData** cmdptr);
u16 ATC_SendCommandEx(SATC* atc, bool dispose, FATC_ResponseCallback callback, bool urgent, u16 cmdid, va_list args);

EATC_Result ATC_OnReceiveData(SATC* atc);
EATC_Result ATC_OnReceiveEcho(SATC* atc, SATC_CommandData* cmd);
EATC_Result ATC_OnReceiveResponse(SATC* atc, SATC_CommandData* cmd);
EATC_Result ATC_OnReceiveOk(SATC* atc, SATC_CommandData* cmd);
EATC_Result ATC_OnReceivePrompt(SATC* atc, SATC_CommandData* cmd);
EATC_Result ATC_OnReceiveError(SATC* atc, SATC_CommandData* cmd);
EATC_Result ATC_OnReceiveEvent(SATC* atc);

EATC_Result ATC_OnReceiveMessage(SATC* atc);
EATC_Result ATC_OnSendCommand(SATC* atc);
EATC_Result ATC_OnServiceEvent(SATC* atc);
EATC_Result ATC_OnDisposeCommands(SATC* atc);

/******************************************************************************
* Internal implementation
******************************************************************************/

void ATC_SetEnableModemShowComm( bool state )
{
	ModemShowComm.Enable = state;
}

bool ATC_GetEnableModemShowComm( void )
{
	return  ModemShowComm.Enable;
}

void ATC_SetAutoEnableModemShowComm( bool state )
{
	ATC_SetEnableModemShowComm(!state);
	ModemShowComm.AutoEnable = state;
}

bool ATC_GetAutoEnableModemShowComm( void )
{
  return ModemShowComm.AutoEnable;
}

bool ATC_IsCommandInProc(SATC_CommandData* cmd) {
	if (cmd->SentTime_ms > 0 && (cmd->Response == EAR_None || cmd->Response == EAR_Data)) {
		return true;
	}

	return false;
}

EATC_Result ATC_GetCommandToSend(SATC* atc, SATC_CommandData** cmdptr) {
	EATC_Result eResult = ATC_NotFound;
	u16 offset, count;

	// Get commands queue count
	count = OBJFIFO_GetCount(atc->CMD_FIFO);

	for (offset = 0; offset < count; offset++) {
		// Check for available command in queue
		if (OBJFIFO_TouchObject(atc->CMD_FIFO, (void**)cmdptr, offset) != FIFO_Ok) {
			eResult = ATC_FifoUnderflow;
			break;
		}

		// Command in process
		if (ATC_IsCommandInProc(*cmdptr)) {
			eResult = ATC_InProc;
			break;
		}

		// This command never sent
		if ((*cmdptr)->SentTime_ms == 0) {
			// This command ready to send if it's dummy (empty) command or delay from last activity is done
			if (strlen(atc->Commands[(*cmdptr)->CommandId].Command) == 0 ||
				SYSTMR_IsTimeOccured_ms(atc->LastActivityTime_ms, atc->NextActivityDelay_ms)) {
				eResult = ATC_Ok;
			}

			break;
		}
	}

	return eResult;
}

EATC_Result ATC_GetPendingCommand(SATC* atc, SATC_CommandData** cmdptr) {
	EATC_Result eResult = ATC_NotFound;
	u16 offset, count;

	// Get commands queue count
	count = OBJFIFO_GetCount(atc->CMD_FIFO);

	for (offset = 0; offset < count; offset++) {
		// Check for available command in queue
		if (OBJFIFO_TouchObject(atc->CMD_FIFO, (void**)cmdptr, offset) != FIFO_Ok) {
			eResult = ATC_FifoUnderflow;
			break;
		}

		// This is no pending command (next command never sent)
		if ((*cmdptr)->SentTime_ms == 0) {
			break;
		}

		if (ATC_IsCommandInProc(*cmdptr)) {
			// Pending command found - exit
			eResult = ATC_Ok;
			break;
		}
	}

	return eResult;
}

u16 ATC_SendCommandEx(SATC* atc, bool dispose, FATC_ResponseCallback callback, bool urgent, u16 cmdid, va_list args) {
	u16 seqnum = 0;
	u16 cmdseqnum;

	// Don't add new command until all pending event will be serviced
	if (urgent || OBJFIFO_GetCount(atc->EVT_FIFO) == 0) {
		cmdseqnum = atc->SequenceNum + 1;

		SATC_CommandData cmd = {
			cmdid,
			"",
			cmdseqnum,
			0,
			EAR_None,
			callback,
			dispose
		};



		// Build command string from format string and parameters
		int len = vsnprintf(cmd.Buffer, ATC_COMMAND_DATA_BUFFER_MAX_LENGTH, atc->Commands[cmdid].Command, args);
		if( len >= ATC_COMMAND_DATA_BUFFER_MAX_LENGTH || len < 0 )
		{
			// The command buffer is not big enough
			if (atc->DebugCallback != NULL) {
				atc->DebugCallback((u8*)&cmd.Buffer[0], ATC_COMMAND_DATA_BUFFER_MAX_LENGTH);
			}
			return 0;
		}


		if(len > 0){
          cmd.Buffer[len] = ATC_CR;
        }

		// Add command to FIFO
		if (OBJFIFO_AddObject(atc->CMD_FIFO, &cmd) == FIFO_Ok) {
			atc->SequenceNum = cmdseqnum;
			seqnum = atc->SequenceNum;
		}



		// Print debug information as defined in specific driver
		if (atc->DebugCallback != NULL) {
			atc->DebugCallback((u8*)&cmd.Buffer[0], len);
		}
	}

	return seqnum;
}

EATC_Result ATC_OnReceiveData(SATC* atc) {
	EATC_Result eResult = ATC_Ok;
	u8 cmdend[ATC_MSG_END_LENGTH] = ATC_MSG_END;
	u8 prompt[ATC_PROMPT_LENGTH] = ATC_PROMPT;

#if defined (__A1) || defined (__F1)
	u8 GrpsPrompt[ATC_GPRS_PROMPT_LENGTH] = ATC_GPRS_PROMPT;
	u8 FilePrompt[ATC_FILE_PROMPT_LENGTH] = ATC_FILE_PROMPT;
    u8 cfPrompt[ATC_CF_PROMPT_LENGTH] = ATC_CF_PROMPT;
    u16 file_prompt_index = 0;
    u16 gprs_prompt_index = 0;
    u16 cf_prompt_index = 0;
#endif

	u16 index = 0, length = 0;
    u8 dummy;

	// Check UART for new data
	if (atc->UART->GetRxQueueCount() == 0) {
		return ATC_NoData;
	}

	// Check for prompt received
	if (atc->UART->FindData( prompt, ATC_PROMPT_LENGTH, &index) == UART_Ok && (index == 0 || index == 1)) {
		length = ATC_PROMPT_LENGTH;
	}

#if defined (__A1) || defined (__F1)
	else if (atc->UART->FindData( GrpsPrompt, ATC_GPRS_PROMPT_LENGTH, &gprs_prompt_index) == UART_Ok && gprs_prompt_index == 0) {
		length = ATC_GPRS_PROMPT_LENGTH;
	}
	else if (atc->UART->FindData( FilePrompt, ATC_FILE_PROMPT_LENGTH, &file_prompt_index) == UART_Ok && file_prompt_index == 0) {
		length = ATC_FILE_PROMPT_LENGTH;
	}
    else if (atc->UART->FindData( cfPrompt, ATC_CF_PROMPT_LENGTH, &cf_prompt_index) == UART_Ok && cf_prompt_index == 0) {
		length = ATC_CF_PROMPT_LENGTH;
    }
#endif

	// Check for end of AT command received
	else {
		index = 0;

		if (atc->UART->FindData( cmdend, ATC_MSG_END_LENGTH, &index) != UART_Ok) {
			eResult = ATC_IncompleteData;
		}
		length = index + ATC_MSG_END_LENGTH;
	}


	length = MIN( length, ATC_MSG_MAX_LENGTH - 1 );

	// Read message to receive buffer
	if (eResult == ATC_Ok) {
		if (length >= ATC_MSG_MAX_LENGTH ||
			atc->UART->Read( (u8*)&ATC_ReceiveBuffer.Data[0], length) != UART_Ok) {
			eResult = ATC_ReceiveError;
		}

		if (eResult == ATC_Ok) {
			// Print debug information as defined in specific driver
			if (atc->DebugCallback != NULL) {
				atc->DebugCallback((u8*)&ATC_ReceiveBuffer.Data[0], length);
			}

			// Update buffer length
			ATC_ReceiveBuffer.Length = length;

            if(ATC_GetEnableModemShowComm()) {
                ATC_ReceiveBuffer.Data[length]= '\0';
                Log(LOG_SUB_MDM,LOG_LVL_INFO,"%s\n", ATC_ReceiveBuffer.Data);
            }
		}
	}

	return eResult;
}

EATC_Result ATC_OnReceiveEcho(SATC* atc, SATC_CommandData* cmd) {
	EATC_Result eResult = ATC_UndefinedMessage;

	u16 length = strlen(atc->Commands[cmd->CommandId].Command);

	// Try to detect echo if it's not dummy zero length command
	if (length != 0 &&
		memcmp((s8*)&ATC_ReceiveBuffer.Data[0], atc->Commands[cmd->CommandId].Command, length) == 0) {
		eResult = ATC_Ok;
	}

	return eResult;
}

EATC_Result ATC_OnReceiveResponse(SATC* atc, SATC_CommandData* cmd) {
	EATC_Result eResult = ATC_UndefinedMessage;
	u16 length;

	if (cmd->Response == EAR_None) {
		// Calculate response length
		length = strlen(atc->Commands[cmd->CommandId].Response);

		if (length == 0 ||
			memcmp((s8*)&ATC_ReceiveBuffer.Data[0], atc->Commands[cmd->CommandId].Response, length) == 0) {
			// Set optimistic result
			eResult = ATC_Ok;


			// Call overrided command callback if exist
			if (cmd->ResponseCallback != NULL) {
				// Pass data only without response string to application
				eResult = cmd->ResponseCallback(cmd->CommandId, (u8*)&ATC_ReceiveBuffer.Data[0],
					ATC_ReceiveBuffer.Length);
			}

			// Call command callback if exist
			else if (atc->Commands[cmd->CommandId].ResponseCallback != NULL) {
				// Pass whole response to the driver
				eResult = atc->Commands[cmd->CommandId].ResponseCallback(cmd->CommandId, (u8*)&ATC_ReceiveBuffer.Data[0],
					ATC_ReceiveBuffer.Length);
			}



			if (eResult != ATC_InProc) {
				// Update commands response
				cmd->Response = EAR_Data;
			}
		}
	}

	return eResult;
}

EATC_Result ATC_OnReceiveOk(SATC* atc, SATC_CommandData* cmd) {
	EATC_Result eResult = ATC_UndefinedMessage;

	if (memcmp((s8*)&ATC_ReceiveBuffer.Data[0], ATC_MSG_OK_STR, strlen(ATC_MSG_OK_STR)) == 0) {
		// Update commands response
		cmd->Response = EAR_Ok;

		eResult = ATC_Ok;
	}

	return eResult;
}

EATC_Result ATC_OnReceivePrompt(SATC* atc, SATC_CommandData* cmd) {
	EATC_Result eResult = ATC_UndefinedMessage;
	char prompt[ATC_PROMPT_LENGTH + 1] = ATC_PROMPT;
	prompt[ATC_PROMPT_LENGTH] = '\0';

	if (memcmp(&ATC_ReceiveBuffer.Data[0], prompt, strlen(prompt)) == 0) {
		// Update commands response
		cmd->Response = EAR_Ok;

		eResult = ATC_Ok;
	}

	return eResult;
}

EATC_Result ATC_OnReceiveError(SATC* atc, SATC_CommandData* cmd) {
	EATC_Result eResult = ATC_UndefinedMessage;
	u16 i;

	for (i = 0; i < atc->ErrorsCount; i++) {
		if (memcmp((s8*)&ATC_ReceiveBuffer.Data[0], atc->Errors[i].Error, strlen(atc->Errors[i].Error)) == 0) {
			// Update commands response
			cmd->Response = EAR_Error;

			// Call default error callback if exist
			if (atc->Errors[i].ErrorCallback != NULL) {
				atc->Errors[i].ErrorCallback(cmd->CommandId, (u8*)&ATC_ReceiveBuffer.Data[0], ATC_ReceiveBuffer.Length);
			}

			// Call overrided command error callback if exist
			if ( ( cmd->ResponseCallback != NULL ) && ( ATC_ReceiveBuffer.Length >= ATC_MSG_END_LENGTH ) ){
				// Pass data only without response string to application
				cmd->ResponseCallback(	cmd->CommandId,
										(u8*)&ATC_ReceiveBuffer.Data[0],
										ATC_ReceiveBuffer.Length - ATC_MSG_END_LENGTH);
			}

			eResult = ATC_Ok;

			break;
		}
	}

	return eResult;
}

EATC_Result ATC_OnReceiveEvent(SATC* atc)
{
	EATC_Result eResult = ATC_NotFound;
	SATC_EventData evt;
	u16 i;
    u32 ToCopy;

	// Save last event without CR/LF
    if(ATC_ReceiveBuffer.Length < 2)
    {
      ToCopy = ATC_ReceiveBuffer.Length;
    }
    else{
      ToCopy = MIN(ATC_ReceiveBuffer.Length - 2, ATC_LAST_DATA_SAVE_SIZE - 1);
    }
	memcpy( LastReceivedData, ATC_ReceiveBuffer.Data, ToCopy );
	LastReceivedData[ToCopy] = '\0';

	for (i = 0; i < atc->EventsCount; i++) {

		if (memcmp((s8*)&ATC_ReceiveBuffer.Data[0], atc->Events[i].Event, strlen(atc->Events[i].Event)) == 0) {
			// Add event to queue if callback exist
			if (atc->Events[i].EventCallback != NULL) {
				// Initialize event data struct before add it to queue
				evt.EventId = i;
				evt.DataLength = ATC_ReceiveBuffer.Length;

				if( evt.DataLength >= ATC_EVENT_DATA_BUFFER_MAX_LENGTH )
				{
					ErrorReport( ERR_MDM_INVALID_EVENT_DATA_SIZE );
					evt.DataLength = ATC_EVENT_DATA_BUFFER_MAX_LENGTH-1;
				}

				memcpy( evt.Data, (s8*)&ATC_ReceiveBuffer.Data[0], evt.DataLength );
				evt.Data[evt.DataLength] = '\0'; // Protect against str*() funcs
				evt.Dispose = false;

				// Add event to queue
				OBJFIFO_AddObject(atc->EVT_FIFO, &evt);
			}

			eResult = ATC_Ok;

			break;
		}
	}

	return eResult;
}

EATC_Result ATC_OnReceiveMessage(SATC* atc) {
	EATC_Result eResult = ATC_Ok;
	SATC_CommandData* cmdptr;

	// Check for incoming data
	eResult = ATC_OnReceiveData(atc);

	if (eResult == ATC_Ok) {

		// Detect if is it response or event (try to find pending command)
		if (ATC_GetPendingCommand(atc, &cmdptr) == ATC_Ok) {

			// Verify that is not just echo
			if (ATC_OnReceiveEcho(atc, cmdptr) != ATC_Ok) {

				// Verify there is no error received
				if (ATC_OnReceiveError(atc, cmdptr) != ATC_Ok) {

					// Try to detect response

					if (ATC_OnReceiveResponse(atc, cmdptr) != ATC_InProc) {

						// Try to detect OK
						if(ATC_OnReceiveOk(atc, cmdptr) != ATC_Ok) {
							ATC_OnReceiveEvent(atc);
						}

						// Try to detect prompt
						ATC_OnReceivePrompt(atc, cmdptr);
					}
				}
			}
		}
		else {
			// Try do detect event if it's no pending command
			ATC_OnReceiveEvent(atc);
		}

		// Save last send / receive command or event delivery activity time
		atc->LastActivityTime_ms = SYSTMR_GetTime_ms();
	}

	return eResult;
}

EATC_Result ATC_OnSendCommand(SATC* atc) {
	EATC_Result eResult = ATC_Ok;
	SATC_CommandData* cmdptr;

	// Get first unsent command
	if (ATC_GetCommandToSend(atc, &cmdptr) == ATC_Ok) {
		// Send command if it's not dummy
		if (strlen(atc->Commands[cmdptr->CommandId].Command) > 0) {
			// Send command data through appropriate UART
			if (atc->UART->Write((u8*)cmdptr->Buffer, strlen(cmdptr->Buffer)) != UART_Ok) {
				eResult = ATC_SendFailed;
			}
            if(ATC_GetEnableModemShowComm()) {
                Log(LOG_SUB_MDM,LOG_LVL_INFO,"%s\n", cmdptr->Buffer);
            }
		}

		// Update sent time
		if (eResult == ATC_Ok) {
			cmdptr->SentTime_ms = SYSTMR_GetTime_ms();
		}
	}

	return eResult;
}

EATC_Result ATC_OnServiceEvent(SATC* atc) {
	EATC_Result eResult = ATC_Ok;
	SATC_EventData evt, *evtptr;

	while (OBJFIFO_TouchObject(atc->EVT_FIFO, (void**)&evtptr, 0) == FIFO_Ok) {
		// Call event callback
		if (atc->Events[evtptr->EventId].EventCallback != NULL) {
			if (atc->Events[evtptr->EventId].EventCallback(evtptr->EventId, (u8*)&evtptr->Data[0], evtptr->DataLength) == ATC_Ok) {
				evtptr->Dispose = true;
			}
		}
		else {
			evtptr->Dispose = true;
		}

		// Remove serviced events
		if (evtptr->Dispose == true) {
			if (OBJFIFO_GetObject(atc->EVT_FIFO, &evt) != FIFO_Ok) {
				eResult = ATC_FifoUnderflow;
				break;
			}
		}
		else {
			break;
		}
	}

	return eResult;
}

EATC_Result ATC_OnDisposeCommands(SATC* atc) {
	EATC_Result eResult = ATC_Ok;
	SATC_CommandData cmd, *cmdptr;

	while (OBJFIFO_TouchObject(atc->CMD_FIFO, (void**)&cmdptr, 0) == FIFO_Ok) {
		// Next command never sent - nothing to do - exit
		if (cmdptr->SentTime_ms == 0) {
			break;
		}

		// Command timeout is done - set response to timeout error
		if (ATC_IsCommandInProc(cmdptr) &&
			SYSTMR_IsTimeOccured_ms(cmdptr->SentTime_ms, atc->Commands[cmdptr->CommandId].Timeout)) {
			Log(LOG_SUB_MDM,LOG_LVL_WARN,"AT_COMMAND timeout: TX[ %s ] RX[ %s ]\n" , 	atc->Commands[cmdptr->CommandId].Command,
																						atc->Commands[cmdptr->CommandId].Response);
			cmdptr->Response = EAR_Timeout;
		}

		// Command life time period is done - dispose
		if (cmdptr->Disposed == false &&
			SYSTMR_IsTimeOccured_ms(cmdptr->SentTime_ms, (atc->Commands[cmdptr->CommandId].Timeout + ATC_COMMAND_LIFETIME_ms))) {
			cmdptr->Disposed = true;
		}

		// Command is disposed - remove it from fifo
		if (cmdptr->Disposed == true && !ATC_IsCommandInProc(cmdptr)) {
			if (OBJFIFO_GetObject(atc->CMD_FIFO, &cmd) != FIFO_Ok) {
				eResult = ATC_FifoUnderflow;
				break;
			}
		}
		else {
			break;
		}
	}

	return eResult;
}

/******************************************************************************
* Interface implementation
******************************************************************************/

void ATC_Init(SATC* atc) {
	OBJFIFO_Clear(atc->CMD_FIFO);
	OBJFIFO_Clear(atc->EVT_FIFO);
	atc->SequenceNum = 0;
	ATC_ReceiveBuffer.Length = 0;
}

u16 ATC_SendCommand(SATC* atc, bool dispose, u16 cmdid, va_list args) {
	return ATC_SendCommandEx(atc, dispose, NULL, false, cmdid, args);
}

u16 ATC_SendCommandOverride(SATC* atc, bool dispose, FATC_ResponseCallback callback, u16 cmdid, va_list args) {
	return ATC_SendCommandEx(atc, dispose, callback, false, cmdid, args);
}

u16 ATC_SendUrgentCommand(SATC* atc, bool dispose, u16 cmdid, va_list args) {
	return ATC_SendCommandEx(atc, dispose, NULL, true, cmdid, args);
}

u16 ATC_SendUrgentCommandOverride(SATC* atc, bool dispose, FATC_ResponseCallback callback, u16 cmdid, va_list args) {
	return ATC_SendCommandEx(atc, dispose, callback, true, cmdid, args);
}

FATC_ResponseCallback ATC_SetEventCallback(SATC* atc, u16 evtid, FATC_ResponseCallback callback) {
	FATC_ResponseCallback prev_callback = atc->Events[evtid].EventCallback;
	atc->Events[evtid].EventCallback = callback;
	return prev_callback;
}

u16 ATC_GetFreeCommandsSpace(SATC* atc) {
	return (OBJFIFO_GetSize(atc->CMD_FIFO) - OBJFIFO_GetCount(atc->CMD_FIFO));
}

EATC_Result ATC_GetCommandResponse(SATC* atc, u32 seqnum, EATC_Response* response) {
	EATC_Result eResult = ATC_Ok;
	SATC_CommandData* cmdptr;
	u16 offset, size;

	*response = EAR_Undefined;

	if ((size = OBJFIFO_GetCount(atc->CMD_FIFO)) == 0) {
		eResult = ATC_NotFound;
	}

	// Find command in queue by sequence number
	for (offset = 0; offset < size; offset++) {
		if (OBJFIFO_TouchObject(atc->CMD_FIFO, (void**)&cmdptr, offset) != FIFO_Ok) {
			eResult = ATC_NotFound;
			break;
		}

		if (cmdptr->SequenceNum == seqnum &&
			cmdptr->Response != EAR_None && cmdptr->Response != EAR_Data) {
			*response = cmdptr->Response;
			cmdptr->Disposed = true;
			break;
		}
	}

	return eResult;
}

EATC_Result ATC_ClosePendingCommand(SATC* atc) {
	EATC_Result eResult = ATC_Ok;
	SATC_CommandData* cmdptr;

	// Close pending command without OK and prepare to next one
	if ((eResult = ATC_GetPendingCommand(atc, &cmdptr)) == ATC_Ok) {
		cmdptr->Response = EAR_Ok;
	}

	return eResult;
}

EATC_Result ATC_OnSendReceive(SATC* atc) {
	EATC_Result eResult = ATC_NotInitialized;

	// Initialize routine
	if (atc->InitCallback != NULL) {
		eResult = atc->InitCallback();
	}

	// Read received response and update appropriate command
	eResult = (EATC_Result)( eResult | ATC_OnReceiveMessage(atc) );

	// Send next command
	eResult = (EATC_Result)( eResult | ATC_OnSendCommand(atc) );

	// Service pending events
	eResult = (EATC_Result)( eResult | ATC_OnServiceEvent(atc) );

	// Dispose all unnecessary commands
	eResult = (EATC_Result)( eResult | ATC_OnDisposeCommands(atc) );

	return eResult;
}


bool ATC_IsCommandsDisposed(SATC* atc) {
	return (OBJFIFO_GetCount(atc->CMD_FIFO) == 0 ? true : false);
}

bool ATC_IsEventsServiced(SATC* atc) {
	return (OBJFIFO_GetCount(atc->EVT_FIFO) == 0 ? true : false);
}

void ATC_DisposeAllCommands(SATC* atc) {
	OBJFIFO_Clear(atc->CMD_FIFO);
}

void ATC_DisposeAllEvents(SATC* atc) {
	OBJFIFO_Clear(atc->EVT_FIFO);
}

u8* ATC_GetLastReceivedData( void )
{
	return LastReceivedData;
}

void ATC_DisposePendingCommand(SATC* atc)
{
    SATC_CommandData* cmdptr;
    if (ATC_GetPendingCommand(atc, &cmdptr) == ATC_Ok) {
        cmdptr->Disposed = true;
    }
}

EATC_Result ATC_GetPendingCommandWithSeqNum(SATC* atc, SATC_CommandData** cmdptr, u16* seqnum) {
    EATC_Result eResult = ATC_NotFound;
    u16 offset, count;

    // Get commands queue count
    count = OBJFIFO_GetCount(atc->CMD_FIFO);

    for (offset = 0; offset < count; offset++) {
        // Check for available command in queue
        if (OBJFIFO_TouchObject(atc->CMD_FIFO, (void**)cmdptr, offset) != FIFO_Ok) {
            eResult = ATC_FifoUnderflow;
            break;
        }

        if ((*cmdptr)->SequenceNum == *seqnum) {
            eResult = ATC_Ok;
            break;
        }
    }

    return eResult;
}

void ATC_DisposeCommandWithSeqNum(SATC* atc, u16* seqnum)
{
    SATC_CommandData* cmdptr;
    if(ATC_GetPendingCommandWithSeqNum(atc, &cmdptr, seqnum) == ATC_Ok) {
        cmdptr->Disposed = true;
    }
}

