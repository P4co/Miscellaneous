from pybrain.supervised.trainers import BackpropTrainer
from pybrain.tools.shortcuts import buildNetwork
from pybrain.datasets.supervised import SupervisedDataSet 

class ReconocedorFirmas():
    """ Esta clase entrena una red neuronal y la prueba """
    #imagen de 2x3
    def __init__ (self):
        """ Inicializa las firmas """
        
        #Individuo1 
        self.individuo11 = [100, 101, 243, 255, 20, 22]
        self.individuo21 = [100, 84, 243, 255, 21, 23]
        self.individuo31 = [100, 101, 243, 195, 22, 24]
        self.individuo41 = [97, 101, 243, 250, 23, 25]
        self.individuo51 = [100, 101, 243, 255, 23, 25]
        self.individuo61 = [100, 101, 243, 255, 23, 25]
        self.individuo71 = [100, 101, 243, 255, 23, 25]
        self.individuo81 = [121, 101, 243, 255, 23, 25]
        
        #Individuo2
        self.individuo12 = [87, 34, 198, 32, 120, 150]
        self.individuo22 = [97, 31, 198, 39, 120, 150]
        self.individuo32 = [87, 32, 198, 32, 120, 150]
        self.individuo42 = [87, 39, 198, 32, 120, 150]
        self.individuo52 = [87, 38, 198, 32, 120, 150]
        self.individuo62 = [86, 37, 198, 32, 120, 150]
        self.individuo72 = [87, 36, 198, 32, 120, 150]
        self.individuo82 = [87, 35, 198, 32, 120, 150]
        
        #Individuo3
        self.individuo13 = [0, 176, 209, 203, 240, 255]
        self.individuo23 = [0, 186, 209, 203, 240, 255]
        self.individuo33 = [5, 176, 209, 203, 240, 255]
        self.individuo43 = [0, 176, 209, 203, 240, 255]
        self.individuo53 = [0, 186, 209, 203, 240, 255]
        self.individuo63 = [3, 176, 209, 203, 240, 255]
        self.individuo73 = [0, 176, 209, 203, 240, 255]
        self.individuo83 = [0, 176, 209, 203, 240, 255]
        self.individuo93 = [10, 176, 209, 203, 240, 255]
        self.individuo10_3 = [0, 176, 209, 203, 240, 255]
        
        #Pruebas
        self.prueba11 = [101, 102, 245, 247, 20, 22]
        self.prueba21 = [103, 105, 247, 249, 20, 22]
        self.prueba31 = [105, 107, 249, 251, 20, 22]
        self.prueba12 = [89, 36, 200, 34, 120, 150]
        self.prueba22 = [91, 38, 202, 36, 120, 150]
        self.prueba32 = [93, 40, 204, 38, 120, 150]
        self.prueba13 = [2, 178, 211, 205, 240, 255]
        self.prueba23 = [4, 180, 213, 209, 240, 255]
        self.prueba33 = [6, 182, 215, 211, 240, 255]
        self.prueba43 = [8, 184, 217, 213, 240, 255]
        
        self.dimension = len(self.individuo11)
    
    
    def realizar_entrenamiento (self):
        """ Realizar el entrenamiento de la red nueronal"""
        self.red_neuronal = buildNetwork(self.dimension, self.dimension, 1)
        self.conjunto_datos = SupervisedDataSet(self.dimension, 1)
        self.conjunto_datos.addSample(self.individuo11,(1,))
        self.conjunto_datos.addSample(self.individuo21,(1,))
        self.conjunto_datos.addSample(self.individuo31,(1,))
        self.conjunto_datos.addSample(self.individuo41,(1,))
        self.conjunto_datos.addSample(self.individuo51,(1,))
        self.conjunto_datos.addSample(self.individuo61,(1,))
        self.conjunto_datos.addSample(self.individuo71,(1,))
        self.conjunto_datos.addSample(self.individuo81,(1,))
        
        self.conjunto_datos.addSample(self.individuo12,(2,))
        self.conjunto_datos.addSample(self.individuo12,(2,))
        self.conjunto_datos.addSample(self.individuo12,(2,))
        self.conjunto_datos.addSample(self.individuo12,(2,))
        self.conjunto_datos.addSample(self.individuo12,(2,))
        self.conjunto_datos.addSample(self.individuo12,(2,))
        self.conjunto_datos.addSample(self.individuo12,(2,))
        self.conjunto_datos.addSample(self.individuo12,(2,))
        
        self.conjunto_datos.addSample(self.individuo13,(3,))
        self.conjunto_datos.addSample(self.individuo23,(3,))
        self.conjunto_datos.addSample(self.individuo33,(3,))
        self.conjunto_datos.addSample(self.individuo43,(3,))
        self.conjunto_datos.addSample(self.individuo53,(3,))
        self.conjunto_datos.addSample(self.individuo63,(3,))
        self.conjunto_datos.addSample(self.individuo73,(3,))
        self.conjunto_datos.addSample(self.individuo83,(3,))
        self.conjunto_datos.addSample(self.individuo93,(3,))
        self.conjunto_datos.addSample(self.individuo10_3,(3,))
        
        obj_propagacion_hacia_atras = BackpropTrainer(self.red_neuronal, self.conjunto_datos)
        self.error = 10
        self.iteracion = 0
        while self.error > 0.01: 
            self.error = obj_propagacion_hacia_atras.train()
            self.iteracion += 1
            
        
    def realizar_pruebas(self):
        """ Prueba la red neuronal con los datos de prueba"""
        print "Iteracion: {0} Error {1}".format(self.iteracion, self.error)
        print "Salidas: ", "+++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++"
        print "Entrada: ", self.prueba11
        print "Salida: ", self.red_neuronal.activate(self.prueba11)
        print
        print "Entrada: ", self.prueba21
        print "Salida: ", self.red_neuronal.activate(self.prueba21)
        print
        print "Entrada: ", self.prueba31
        print "Salida: ", self.red_neuronal.activate(self.prueba31)
        print
        print "Entrada: ", self.prueba12
        print "Salida: ", self.red_neuronal.activate(self.prueba12)
        print
        print "Entrada: ", self.prueba22
        print "Salida: ", self.red_neuronal.activate(self.prueba22)
        print
        print "Entrada: ", self.prueba32
        print "Salida: ", self.red_neuronal.activate(self.prueba32)
        print
        print "Entrada: ", self.prueba13
        print "Salida: ", self.red_neuronal.activate(self.prueba13)
        print
        print "Entrada: ", self.prueba23
        print "Salida: ", self.red_neuronal.activate(self.prueba23)
        print
        print "Entrada: ", self.prueba33
        print "Salida: ", self.red_neuronal.activate(self.prueba33)
        print
        print "Entrada: ", self.prueba43
        print "Salida: ", self.red_neuronal.activate(self.prueba43)
        print "Salidas: ", "+++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++"
        
if __name__ == "__main__":
        reconocedor = ReconocedorFirmas()
        reconocedor.realizar_entrenamiento()
        reconocedor.realizar_pruebas()
        