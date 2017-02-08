//
//  BodyView.swift
//  IOSQuiz
//
//  Created by Inssoftjma on 12/13/16.
//  Copyright © 2016 Inssoftjma. All rights reserved.
//

import UIKit

class BodyView: UIView {
    var backgroundView : UIView!
    var usuarioText : UITextField!
    var passwordText : UITextField!
    var imagen : UIImageView!
    
    override init(frame: CGRect){
        super.init(frame: frame)
        //llamar la f
        initProperties()
    }
    
    required init?(coder aDecoder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }
    
    func initProperties(){
        backgroundView = UIView()
        backgroundView.backgroundColor = UIColor(patternImage: #imageLiteral(resourceName: "Fondo"))
        
        //backgroundView.backgroundColor = UIColor.lightGray
        backgroundView.frame = CGRect(x: self.frame.origin.x,
                                      y: self.frame.origin.y,
                                      width: self.frame.width * 1,
                                      height: self.frame.height * 1)

        usuarioText = UITextField()
        usuarioText.textAlignment = .center
        usuarioText.textColor = UIColor.white
        usuarioText.frame = CGRect(x: self.frame.origin.x + self.frame.width * 0.1,
                                   y: self.frame.origin.y + self.frame.height * 0.1,
                                   width: self.frame.width * 0.8,
                                   height: self.frame.height * 0.07)
        usuarioText.backgroundColor = UIColor.darkGray
        usuarioText.alpha = 0.6
        usuarioText.layer.cornerRadius = 20
        usuarioText.layer.borderWidth = 3
        
        
        passwordText = UITextField()
        passwordText.textAlignment = .center
        passwordText.textColor = UIColor.white
        passwordText.frame = CGRect(x: self.frame.origin.x + self.frame.width * 0.1,
                                    y: self.frame.origin.y + self.frame.height * 0.2,
                                    width: self.frame.width * 0.8,
                                    height: self.frame.height * 0.07)
        passwordText.backgroundColor = UIColor.darkGray
        passwordText.alpha = 0.6
        passwordText.layer.cornerRadius = 20
        passwordText.layer.borderWidth = 3
        
        
        imagen = UIImageView()
        imagen.image = #imageLiteral(resourceName: "Xcode6_2x")
        imagen.frame = CGRect(x: self.frame.origin.x + self.frame.width * 0.0,
                              y: self.frame.origin.y + self.frame.height * 0.25,
                              width: self.frame.width * 1,
                              height: self.frame.height * 0.7)
        imagen.alpha = 0.6
        imagen.isUserInteractionEnabled = true
        
        backgroundView.addSubview(imagen)
        backgroundView.addSubview(usuarioText)
        backgroundView.addSubview(passwordText)
        self.addSubview(backgroundView)
    }

}
