import pdb
import poc_ttt_gui
import poc_ttt_provided as provided
 
#web in skulpor
#import codeskulptor
#codeskulptor.set_timeout(60)
 
# SCORING VALUES - DO NOT MODIFY
SCORES = {provided.PLAYERX: 1,
          provided.DRAW: 0,
          provided.PLAYERO: -1}
 
def mm_move(board, player):
    """
    Make a move on the board.
 
    Returns a tuple with two elements. The first element is the score
    of the given board and the second element is the desired move as a
    tuple, (row, col).
    """
    """ If you want to debug the code use the pdb """
    #pdb.set_trace() 
    
    #print ("Board:\n", board, "Player: ", player, "\n")
    winner = board.check_win()
    if winner:
        return SCORES[winner], (-1, -1)
    opponent = provided.switch_player(player)
    best_score, best_move = SCORES[opponent], (-1, -1)
    for move in board.get_empty_squares():
        opponent_board = board.clone()
        opponent_board.move(move[0], move[1], player)
        winner = opponent_board.check_win()        
        if winner == player:
            #print(winner,player)
            #pdb.set_trace()
            return SCORES[player], move
        score = mm_move(opponent_board, opponent)[0]
        if score*SCORES[player] > best_score*SCORES[player]:
            best_score = score
            best_move = move
    return best_score, best_move
 
def move_wrapper(board, player, trials):

    move = mm_move(board, player)
    assert move[1] != (-1, -1), "not permited, illegal move (-1, -1)"
    return move[1]
 
 
#provided.play_game(move_wrapper, 1, False)
poc_ttt_gui.run_gui(3, provided.PLAYERO, move_wrapper, 1, False)
