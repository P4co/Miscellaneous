import cPickle #Permite guardar y abrir archivos rapidamente
from numpy import *

class nn:
    def sigmoid(self,x):
        return tanh(x)
    # derivada de la funcion sigmoid
    def dsigmoid(self,y):
        #d/dx(tanh(x))=1 - (tanh(x))^2 
        return 1.0-y*y

    def __init__(self, ni, nh, no):
        # numero de nodos entrada, oculta y salidas
        self.ni = ni +1 # +1 para los bias
        self.nh = nh
        self.no = no
       
        # activacion de los nodos
        self.ai = ones((self.ni))
        self.a1 = ones((self.nh))
        self.ao = ones((self.no))
        
        # crear pesos
        self.w1 = random.uniform(-2.0,2.0,(self.ni, self.nh))
        self.w2 = random.uniform(-2.0,2.0,(self.nh, self.no))
        
    def guardar(self,filename):
         W = [self.w1,self.w2]
         cPickle.dump(W,open(filename,'w'))

    def cargar(self,filename):
         W = cPickle.load(open(filename,'r'))
         self.w1=W[0]
         self.w2=W[1]

    def evaluar(self, inputs):
        if len(inputs) != self.ni-1:
            raise ValueError, 'numero erroneo de entradas'

        # activaciones entradas
        self.ai[0:self.ni-1]=inputs

        # acticaciones capa oculta
        # a1=f1(W1*p)
        self.n1 = dot(transpose(self.w1),self.ai)
        self.a1= self.sigmoid(self.n1)

        # activaciones salidas
        # a2=f2(W2* a1)
        self.n2 = dot(transpose(self.w2),self.a1)
        self.ao = self.sigmoid(self.n2)

        return self.ao

    def backPropagate(self, targets, N):
        if len(targets) != self.no:
            raise ValueError, 'numero de objetivos incorrectos'

        #Propagar errores hacia atras        
        # Calcular errores a la salida
        d2=targets-self.ao
        
        # Calcular errores en la capa oculta
        d1 = dot(self.w2,d2)
      
        # Acutalizar pesos de la salida
        #Wnuevo += Wviejo+n*(delta2*f2')*a1
        d2fp= self.dsigmoid(self.ao) * d2
        change = d2fp * reshape(self.a1,(self.a1.shape[0],1))
        self.w2 = self.w2 + N * change

        #Actualizar pesos de las entradas
        #Wnuevo += Wviejo+n*(delta1*f1')*a0
        d1fp =self.dsigmoid(self.a1) * d1
        change = d1fp * reshape(self.ai,(self.ai.shape[0],1))
        self.w1 = self.w1 + N * change

        # calcular error
        error = sum(0.5* (targets-self.ao)**2)
        
        return error

    def test(self, entrada):
        #Imprime la entrada y su salida de la red neuronal
        for p in range(size(entrada,axis=0)):
            print entrada[p,:], '->', self.evaluar(entrada[p,:])

    def singletrain(self,inputs,targets):
        #Realiza una iteracion del entrenamiento backpropagation
        self.evaluar(inputs)
        return self.backPropagate(targets,0.5)
       
    def train(self, entrada, salida, iterations=1000, N=0.5):
        #Realiza entrenamiento backpropagation        
        # N: factor de aprendizaje
        for i in xrange(iterations):
            error = 0.0
            for p in range(size(entrada,axis=0)):
                inputs = entrada[p,:]
                targets = salida[p,:]
                self.evaluar(inputs)
                error = error + self.backPropagate(targets, N)
            if i % 100 == 0 and i!=0:
                print 'error ' + str(error)
        #print error
        
