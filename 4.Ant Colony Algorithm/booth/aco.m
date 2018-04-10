

clc;
clear;
close all;

%% Definición del problema

model=CreateModel();

CostFunction=@(p) MyCost(p,model);

nVar=model.n;

%% Pametros de ACO

MaxIt=500;      % Maximo numero de iteraciones

nAnt=50;        % Tamaño de la población

Q=1;

tau0=10;        % Pheromona inicial

alpha=0.3;      % Peso de la feromona

rho=0.1;       %Radio de evaporación


%% Inicializacion

tau=tau0*ones(model.m,nVar);

BestCost=zeros(MaxIt,1);    % Arreglo que almacenara los resultados

% Hormigas nueva
empty_ant.Tour=[];
empty_ant.Cost=[];

% Matriz de la hormiga
ant=repmat(empty_ant,nAnt,1);

% Mejor Hormiga
BestSol.Cost=inf;


%% Loop principal

for it=1:MaxIt
    
    % Movimiento de hormiga
    for k=1:nAnt
        
        ant(k).Tour=[];
        
        for l=1:nVar
            
            P=tau(:,l).^alpha;
            
            P(ant(k).Tour)=0;
            
            P=P/sum(P);
            
            j=RouletteWheelSelection(P);
            
            ant(k).Tour=[ant(k).Tour j];
            
        end
        ant(k).Cost=CostFunction(ant(k).Tour);
        if ant(k).Cost<BestSol.Cost
            BestSol=ant(k);
        end
        
    end
    
    % Actualización de feromonas
    for k=1:nAnt
        
        tour=ant(k).Tour;
        
        for l=1:nVar
            
            tau(tour(l),l)=tau(tour(l),l)+Q/ant(k).Cost;
            
        end
        
    end
    
    % Evaporación
    tau=(1-rho)*tau;
    
    % Mejor costo almacendo
    BestCost(it)=BestSol.Cost;
    
    % Información de la iteración
    disp(['Iteration ' num2str(it) ': Best Cost = ' num2str(BestCost(it))]);

    % Plot Solucion
    figure(1);
    PlotSolution(BestSol.Tour,model);
    pause(0.01);
    
end

%% Resultados

figure;
plot(BestCost,'LineWidth',2);
xlabel('Iteration');
ylabel('Best Cost');
grid on;
