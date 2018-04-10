'''
############
DFS Y BFS algorithm
Francisco Espinel
Patricia Guzman
Galo Gardenas
Diego Gonzales
Fernanda Suarez
############

The variable board_Init, it will be the board that is going to inicializate
The variable board_Goal, it will be the board you want to reach

Change these variables to check te code

'''

from collections import deque
import pdb

userChar = {1: 'X', -1: 'O', 0: '-'}

board_Init = [1,0,0,
              0,0,0,
              0,0,0]

board_Goal = [1,0,0,
              0,1,0,
              0,-1,0]
nodes = [0 for i in range(9)]



def isValidplays(board,position,PlayerTurn):

    check_board = board[:]
    if(check_board[position] == 1 or check_board[position] == -1):
        Isvalid = False
        #print('Tablero ocupado')
    else:
        Isvalid = True
        check_board[position] = PlayerTurn
        #print ('Jugada Aceptada')
        #print ('Jugada Aceptada',check_board)

    #print('the board fo check is:',board)
    return (Isvalid, check_board)

def display(board):
    print(' _ _ _\n'+''.join('|'+userChar[board[i]]+('|\n' if i%3==2 else '') for i in range(9)))


def checkInputs(board):
    Xlength = board.count(1)
    Olength = board.count(-1)    
    if (Olength > Xlength+1) or ( Xlength > Olength+1):
        print('Error Jugada no Valida')
        playerTurn =0
        return (False,playerTurn)
    
    if (Olength == Xlength):
        #the start player could be any I will assume O
        playerTurn = -1
    elif (Olength > Xlength):
        playerTurn = 1
    elif (Xlength > Olength):
        playerTurn = -1
    else:
        print('Tablero no valido')
        playerTurn=0
        return (False,playerTurn)

    #print('Tablero Valido, el jugador que inicia es:',userChar[playerTurn])
    return (True,playerTurn)
        
    


def next_move(playerTurn):
    if playerTurn == 1:
        NextTurn = -1;
    elif playerTurn == -1:
        NextTurn= 1
    else:
        print("Error")

    return(NextTurn)




def dfs_algorithm (board_init,board_goal,StartPlayer):
    Open = []
    Closed = []
    IsFinishedOk = False
    #NextPlayer = StartPlayer
    playerTurn = StartPlayer
    Open.append(board_init)
    #pdb.set_trace()
    while len(Open) != 0:
        #remove state from open, call it X
        X = Open.pop()
        #display (X)        
        if X == board_Goal:
            print ('Finalizo algorimto DFS, el tablero que llegó es::')
            display (X)
            print ('Numero de jugadas',len(Closed))  #Number of plays
            #print (Closed)
            IsFinishedOk = True
            return (Closed, IsFinishedOk)
        else:
            #Generate the children in this case all the plays                        
            validPlays = [];
            for i in range(len(nodes)):
                valid, new_Board = isValidplays(X,i,playerTurn)
                if valid == True:
                    validPlays.append(new_Board)

            Closed.append(X) #Put X on closed
            #pdb.set_trace()
            #discard children of X if already on closed
            filtered_plays =[]
            for plays in validPlays:
                if not(plays in Closed):
                    filtered_plays.append(plays)

            #pdb.set_trace()
            #print(filtered_plays)
            #put remaining children on left end of open
            for plays in filtered_plays:
                Open.append(plays)

            playerTurn = next_move(playerTurn)


    print('No logro nada')
    return (Closed, IsFinishedOk)
                


def bfs_algorithm (board_init,board_goal,StartPlayer):
    Open = deque([])
    Closed = deque([])
    IsFinishedOk = False
    #NextPlayer = StartPlayer
    playerTurn = StartPlayer
    Open.appendleft(board_init)
    #pdb.set_trace()
    while len(Open) != 0:
        #remove state from open, call it X
        X = Open.popleft()
        #display (X)        
        if X == board_Goal:
            print ('Finalizo algorimto BFS, el tablero que llegó es:')
            display (X)
            print ('Numero de jugadas',len(Closed))  #Number of plays
            #print (Closed)
            IsFinishedOk = True
            return (Closed, IsFinishedOk)
        else:
            #Generate the children in this case all the plays                        
            validPlays = [];
            for i in range(len(nodes)):
                valid, new_Board = isValidplays(X,i,playerTurn)
                if valid == True:
                    validPlays.append(new_Board)

            Closed.appendleft(X) #Put X on closed
            #pdb.set_trace()
            #discard children of X if already on closed
            filtered_plays =[]
            for plays in validPlays:
                if not(plays in Closed):
                    filtered_plays.append(plays)

            #pdb.set_trace()
            #print(filtered_plays)
            #put remaining children on left end of open
            for plays in filtered_plays:
                Open.append(plays)

            playerTurn = next_move(playerTurn)


    print('No logro nada')
    return (Closed, IsFinishedOk)

                     


def main():


# Test Code
#    valid, newboard = isValidplays(board_Goal,2,-1)
#    display(newboard)
#    dfs_algorithm (board_Init,board_Goal,-1)


    validInit, nextPlayer = checkInputs(board_Init)
    validGoal, nextPlayer = checkInputs(board_Goal)
    if (validGoal==True) and (validInit == True) :
        print ('Tablero inicial')
        display(board_Init)
        print ('Jugada a llegar')
        display(board_Goal)
    
    print('----------Algoritmo DFS inicia----------')
    dfs_algorithm (board_Init,board_Goal,nextPlayer)
    print('----------Algoritmo BFS inicia-----------')
    bfs_algorithm (board_Init,board_Goal,nextPlayer)
    
   

    
if __name__ == '__main__':
    main()








    

