//
//  LoginViewController.swift
//  IOSQuiz
//
//  Created by Inssoftjma on 12/13/16.
//  Copyright © 2016 Inssoftjma. All rights reserved.
//

import UIKit

class LoginViewController: UIViewController {
    
    var loginView : Login!
    
    override func viewDidLoad() {
        super.viewDidLoad()
        
        iniciarPropiedades()
        
    }
    
    func iniciarPropiedades(){
        loginView = Login(frame: UIScreen.main.bounds)
        
        loginView.nextButton.addTarget(self, action: #selector(nextView(sender:)), for: .touchUpInside)
    
        self.view.addSubview(loginView)
    }
    
    func nextView(sender: UIButton){
        let usuario : String = String(loginView.usuarioText.text!)!
        let password : String = String(loginView.passwordText.text!)!
        
        UserDefaults.standard.set(usuario, forKey: "usuario")
        UserDefaults.standard.set(password, forKey: "password")
        
        self.present(BodyViewController(), animated: true, completion: nil)
        
    }
    
}
