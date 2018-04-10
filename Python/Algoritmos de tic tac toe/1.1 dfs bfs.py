import pdb
#Literal 1 deber
def recursive_dfs(graph, start, path=[]):
  '''recursive depth first search from start'''
  #pdb.set_trace()
  path=path+[start]
  for node in graph[start]:
    if not node in path:
      path=recursive_dfs(graph, node, path)
  return path


def iterative_bfs(graph, start, path=[]):
  '''iterative breadth first search from start'''
  q=[start]
  while q:
    v=q.pop(0)
    if not v in path:
      path=path+[v]
      q=q+graph[v]
  return path

'''
   +---- A
   |   /   \
   |  B--D--C
   |   \ | /
   +---- E
'''

graph = {'A':['B','C'],'B':['D','E'],'C':['D','E'],'D':['E'],'E':['D'],'E':['G'],'G':['E']}
print ('recursive dfs ', recursive_dfs(graph, 'A'))
print ('iterative bfs ', iterative_bfs(graph, 'A'))

