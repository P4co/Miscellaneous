from nnback import nn
from csv import reader
from numpy import *
import os
from csv import reader
import csv, operator


# Carga de archivo
def load_csv(filename):
    dataset = list()
    with open(filename, 'r') as file:
        csv_reader = reader(file)
        for row in csv_reader:
            if not row:
                continue
            dataset.append(row)
    return dataset


# convertir a float
def str_column_to_float( dataset, column):
    for row in dataset:
        row[column] = float(row[column].strip())


def Database( filename):
    data = load_csv(filename)
    for i in range(len(data[0])):
        str_column_to_float(data, i)
    sent_data = array(data)
    return sent_data



def main():

    print ("Opciones para la red neuronal")
    print ("1. entrenamiento y guardar datos")
    print ("2. cargar datos y simular la red")
    print ("3. salir")

main()

op = raw_input("Elegir una opcion: ")
while True:
        if(op == '1'):
            prueba = nn(4,10,1)

            entradas = Database('iris_data_entrada.csv')
            salidas = Database('iris_data_salida.csv')
            print salidas
            print entradas

            print("COMIENZA ENTRENAMIENTO")
            print("")
            prueba.train(entradas,salidas)

            print("")
            prueba.test(entradas)
            print("Se ha guardado el entrenamiento correctamente con el nombre de entrenamiento.txt")

            print("")
            prueba.guardar("entrenamiento.txt")
            print("")
            break

        elif(op == '2'):
            print("")
            prueba = nn(4,30,1)
            print("se cargan los pesos y el proceso se hace interno")
            prueba.cargar("entrenamiento.txt")
            print("")


            print("RED ENTRENADA")
            print("COMIENZA LA SIMULACION")
            print("")

            entradasTest = Database('iris_data_entrada.csv')
            prueba.test(entradasTest)
            break

        elif(op == '3'):
            break
        else:
            print ("")
            raw_input("No has pulsado ninguna opcion correcta.. \npulsa una tecla para continuar")

