//
//  BodyViewController.swift
//  IOSQuiz
//
//  Created by Inssoftjma on 12/13/16.
//  Copyright © 2016 Inssoftjma. All rights reserved.
//

import UIKit

class BodyViewController: UIViewController {
    var bodyView : BodyView!
    
    override func viewDidLoad() {
        super.viewDidLoad()
        
        initProperties()
        
    }
    
    func initProperties(){
        bodyView = BodyView(frame: UIScreen.main.bounds)
        //
        bodyView.usuarioText.text = "\(UserDefaults.standard.string(forKey: "usuario")!)"
        bodyView.passwordText.text = "\(UserDefaults.standard.string(forKey: "password")!)"
        
        bodyView.imagen.addGestureRecognizer(UITapGestureRecognizer(target: self, action: #selector(alert(sender:))))
        
        self.view.addSubview(bodyView)
    }
    
    func alert(sender: UIImageView){
        
        if bodyView.imagen.image ==  #imageLiteral(resourceName: "Xcode6_2x") {
            
            let alert = UIAlertController (title: "Alert", message: "I AM AN ALERT", preferredStyle: UIAlertControllerStyle.alert)
            alert.addAction(UIAlertAction(title: "Click", style: UIAlertActionStyle.default, handler: nil))
            self.present(alert, animated: true, completion: nil)
        }
    }

   
}
