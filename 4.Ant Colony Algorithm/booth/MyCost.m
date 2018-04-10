

function z=MyCost(p,model)

    x=model.x;
    y=model.y;
    x1 = x(1);
    x2 = x(2);
    z=0;
    for i=1:n-1
        for j=i+1:n
        x1=x(p(i));
        x2=y(p(i));
        parte1 = (x1+ 2*x2 - 7)^2;  
        parte2 = (2*x1 + x2 -5)^2;
        z = z+parte1 + parte2;            
        end
    end

end