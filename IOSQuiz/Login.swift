//
//  Login.swift
//  IOSQuiz
//
//  Created by Inssoftjma on 12/13/16.
//  Copyright © 2016 Inssoftjma. All rights reserved.
//

import UIKit

class Login: UIView {
    var backgroundView : UIView!
    var usuarioLabel : UILabel!
    var usuarioText : UITextField!
    var passwordLabel : UILabel!
    var passwordText : UITextField!
    var nextButton : UIButton!
    
    override init(frame: CGRect){
        super.init(frame: frame)
        //llamar la f
        iniciarPropiedades()
    }
    
    required init?(coder aDecoder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }
    
    func iniciarPropiedades(){
        backgroundView = UIView()
        backgroundView.backgroundColor = UIColor(patternImage: #imageLiteral(resourceName: "Fondo"))

        //backgroundView.backgroundColor = UIColor.lightGray
        backgroundView.frame = CGRect(x: self.frame.origin.x,
                              y: self.frame.origin.y,
                              width: self.frame.width * 1,
                              height: self.frame.height * 1)
        
        usuarioLabel = UILabel()
        usuarioLabel.text = "Usuario:"
        usuarioLabel.textColor = UIColor.white
        usuarioLabel.font = UIFont.boldSystemFont(ofSize: 16.0)
        usuarioLabel.textAlignment = .center
        usuarioLabel.frame = CGRect(x: self.frame.origin.x + self.frame.width * 0.1,
                               y: self.frame.origin.y + self.frame.height * 0.1,
                               width: self.frame.width * 0.8,
                               height: self.frame.height * 0.1)
        
        usuarioText = UITextField()
        usuarioText.textAlignment = .center
        usuarioText.textColor = UIColor.white
        usuarioText.frame = CGRect(x: self.frame.origin.x + self.frame.width * 0.1,
                                   y: self.frame.origin.y + self.frame.height * 0.2,
                                   width: self.frame.width * 0.8,
                                   height: self.frame.height * 0.07)
        usuarioText.backgroundColor = UIColor.darkGray
        usuarioText.alpha = 0.6
        usuarioText.layer.cornerRadius = 20
        usuarioText.layer.borderWidth = 3
        
        //passwordLabel = UILabel()
        //passwordLabel.text = "Password:"
        passwordLabel.textColor = UIColor.white
        passwordLabel.font = UIFont.boldSystemFont(ofSize: 16.0)
        passwordLabel.textAlignment = .center
        passwordLabel.frame = CGRect(x: self.frame.origin.x + self.frame.width * 0.1,
                                    y: self.frame.origin.y + self.frame.height * 0.3,
                                    width: self.frame.width * 0.8,
                                    height: self.frame.height * 0.1)

        passwordText = UITextField()
        passwordText.textAlignment = .center
        passwordText.textColor = UIColor.white
        passwordText.frame = CGRect(x: self.frame.origin.x + self.frame.width * 0.1,
                                  y: self.frame.origin.y + self.frame.height * 0.4,
                                  width: self.frame.width * 0.8,
                                  height: self.frame.height * 0.07)
        passwordText.backgroundColor = UIColor.darkGray
        passwordText.alpha = 0.6
        passwordText.layer.cornerRadius = 20
        passwordText.layer.borderWidth = 3
        passwordText.isSecureTextEntry = true
        
        nextButton = UIButton()
        nextButton.titleLabel?.textColor = UIColor.white
        nextButton.frame = CGRect(x: self.frame.origin.x + self.frame.width * 0.3,
                             y: self.frame.origin.y + self.frame.height * 0.6,
                             width: self.frame.width * 0.4,
                             height: self.frame.height * 0.1)
        nextButton.backgroundColor = UIColor.red
        nextButton.alpha = 0.5
        nextButton.setTitle("Siguiente", for: .normal)
        nextButton.layer.cornerRadius = 20
        nextButton.layer.borderWidth = 5
        nextButton.layer.borderColor = UIColor.gray.cgColor
        
        backgroundView.addSubview(usuarioLabel)
        backgroundView.addSubview(usuarioText)
        backgroundView.addSubview(passwordLabel)
        backgroundView.addSubview(passwordText)

        backgroundView.addSubview(nextButton)
        self.addSubview(backgroundView)
    }
}
