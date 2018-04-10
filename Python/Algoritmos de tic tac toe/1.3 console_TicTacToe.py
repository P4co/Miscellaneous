ttt = [0 for i in range(9)]
lines = [[0, 1, 2],[3, 4, 5],[6, 7, 8],[0, 3, 6],[1, 4, 7],[2, 5, 8],[0, 4, 8],[2, 4, 6],[2,4,7],[0,4,8]]
lineValues = [0 for i in range(8)]
userChar = {1: "O", -1: "X", 0: "_"}
turn = -1 # defalut to user move first

#*****************************************************
def main():
    global userChar, turn
    if input("Empiezo la computadora? (S/N)").lower()=="s":
        userChar = {1:"X",-1:"O",0:"_"}
        turn = 1
    display()
    while not hasWinner():
        if 0 in ttt:
            nextMove(turn)
            turn *= -1
            display()
        else:
            print("Es un empate!")
            break
#*****************************************************
def hasWinner():
    #print ('LINEVALUES,hasWinner',lineValues)
    if max(lineValues) == 3:
        print("********  Yo Gano, y tu pierdes jajaja!!  ********")
        return True
    elif min(lineValues) == -3:
        print("********  Tu ganas :( ********")
        return True
#*****************************************************
def nextMove(turn):
    if turn== -1: #turno de usuario
        print("Es tu turno (" + userChar[-1]+"):")
        while not isUserMoveSuccessful(input("Numero de la celda:")):
            print("Ingrese nuevamente!")
    else: #Turno de AI
        print("Turno de la computadora loading...")
        for lineValue in [2,-2,-1,1,0]:
            #print(lineValue)
            cell = findMostValuableCell(lineValue)
            print('CELL',cell)
            if cell>=0: #celda enocontrada para ser usada
                markCell(cell, turn)
                print ("La celda elgida fue ", str(cell),"." )
                return
#*****************************************************
def isUserMoveSuccessful(userInput):
    s = list(userInput)[0]
    if '012345678'.find(s)>=0 and ttt[int(s)]==0:
        markCell(int(s), turn)
        return True
#*****************************************************
def findMostValuableCell(lineValue):
    #print ('TTT es:',ttt)
    if set(ttt)=={0}:        
        return 1
    allLines = [i for i in range(8) if lineValues[i]==lineValue]
    print('ALLLINES',allLines)
    allCells =[j for line in allLines for j in lines[line] if ttt[j]==0]
    cellFrequency = dict((c, allCells.count(c)) for c in set(allCells))
    if len(cellFrequency)>0: # get the cell with highest frequency.
        return max(cellFrequency, key=cellFrequency.get)
    else:
        return -1
#*****************************************************
def markCell(cell, trun):
    global lineValues, ttt
    ttt[cell]=turn
    lineValues = [sum(cellValue) for line in lines for cellValue in [[ttt[j] for j in line]]]
#*****************************************************
def display():
    print(' _ _ _\n'+''.join('|'+userChar[ttt[i]]+('|\n' if i%3==2 else '') for i in range(9)))
#*****************************************************
main()
